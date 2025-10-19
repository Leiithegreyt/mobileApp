# Backend Single Trip Status Implementation

## Overview

This document outlines the backend changes required to support the single trip status flow that matches the frontend implementation. The frontend now uses status-based buttons that change dynamically based on the trip's current state.

## Status Flow for Single Trips

```
pending → on_route → arrived → returning → completed
   ↓         ↓         ↓         ↓         ↓
Depart   Arrived   Return to  Arrived at Complete
         Button    Base       Base       Trip
```

## Required Backend Changes

### 1. Database Schema Updates

#### A. Add Status Column to Trips Table
```sql
-- Migration: Add status column to trips table
BEGIN;

-- Add status column if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'trips' AND column_name = 'status') THEN
        ALTER TABLE trips ADD COLUMN status VARCHAR(20) DEFAULT 'pending';
    END IF;
END $$;

-- Update existing trips
UPDATE trips SET status = 'pending' WHERE status IS NULL;

-- Add check constraint for valid status values
ALTER TABLE trips ADD CONSTRAINT check_trip_status 
CHECK (status IN ('pending', 'on_route', 'arrived', 'returning', 'completed'));

COMMIT;
```

#### B. Optional: Status History Tracking
```sql
-- Create status history table for audit trail
CREATE TABLE trip_status_history (
    id SERIAL PRIMARY KEY,
    trip_id INTEGER REFERENCES trips(id),
    status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by INTEGER REFERENCES users(id)
);

-- Create index for performance
CREATE INDEX idx_trip_status_history_trip_id ON trip_status_history(trip_id);
CREATE INDEX idx_trip_status_history_changed_at ON trip_status_history(changed_at);
```

### 2. API Endpoint Updates

#### A. Update Departure Endpoint
```python
@router.post("/trips/{trip_id}/departure")
async def log_departure(trip_id: int, body: DepartureBody, db: Session = Depends(get_db)):
    # Get the trip
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    
    # Validate status transition
    if trip.status not in ["pending", "approved"]:
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid status transition from {trip.status} to on_route"
        )
    
    # ... existing departure logging logic ...
    
    # Update trip status
    trip.status = "on_route"
    db.commit()
    
    # Optional: Log status change
    log_status_change(db, trip_id, "on_route", current_user.id)
    
    return {"message": "Departure logged successfully", "status": "on_route"}
```

#### B. Update Arrival Endpoint
```python
@router.post("/trips/{trip_id}/arrival")
async def log_arrival(trip_id: int, body: ArrivalBody, db: Session = Depends(get_db)):
    # Get the trip
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    
    # Validate status transition
    if trip.status not in ["on_route", "in_progress"]:
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid status transition from {trip.status} to arrived"
        )
    
    # ... existing arrival logging logic ...
    
    # Update trip status
    trip.status = "arrived"
    db.commit()
    
    # Optional: Log status change
    log_status_change(db, trip_id, "arrived", current_user.id)
    
    return {"message": "Arrival logged successfully", "status": "arrived"}
```

#### C. Add Return Start Endpoint
```python
@router.post("/trips/{trip_id}/return-start")
async def start_return(trip_id: int, body: ReturnStartRequest, db: Session = Depends(get_db)):
    # Get the trip
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    
    # Validate status transition
    if trip.status != "arrived":
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid status transition from {trip.status} to returning"
        )
    
    # ... existing return start logging logic ...
    
    # Update trip status
    trip.status = "returning"
    db.commit()
    
    # Optional: Log status change
    log_status_change(db, trip_id, "returning", current_user.id)
    
    return {"message": "Return started successfully", "status": "returning"}
```

#### D. Add Return Arrival Endpoint
```python
@router.post("/trips/{trip_id}/return-arrival")
async def return_arrival(trip_id: int, body: ReturnArrivalRequest, db: Session = Depends(get_db)):
    # Get the trip
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    
    # Validate status transition
    if trip.status != "returning":
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid status transition from {trip.status} to completed"
        )
    
    # ... existing return arrival logging logic ...
    
    # Update trip status
    trip.status = "completed"
    db.commit()
    
    # Optional: Log status change
    log_status_change(db, trip_id, "completed", current_user.id)
    
    return {"message": "Return completed successfully", "status": "completed"}
```

### 3. Status Validation Functions

```python
def validate_status_transition(current_status: str, new_status: str) -> bool:
    """
    Validate if a status transition is allowed
    """
    valid_transitions = {
        "pending": ["on_route"],
        "on_route": ["arrived"],
        "arrived": ["returning", "completed"],  # Can skip returning for direct completion
        "returning": ["completed"],
        "completed": []  # Terminal state
    }
    
    return new_status in valid_transitions.get(current_status, [])

def log_status_change(db: Session, trip_id: int, new_status: str, user_id: int):
    """
    Log status change to history table (optional)
    """
    try:
        status_history = TripStatusHistory(
            trip_id=trip_id,
            status=new_status,
            changed_by=user_id
        )
        db.add(status_history)
        db.commit()
    except Exception as e:
        # Log error but don't fail the main operation
        logger.error(f"Failed to log status change: {e}")
```

### 4. Update Trip Details Response

Ensure the `/trips/{id}` endpoint returns the current status:

```python
@router.get("/trips/{trip_id}")
async def get_trip_details(trip_id: int, db: Session = Depends(get_db)):
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    
    return {
        "id": trip.id,
        "status": trip.status,  # Ensure this is included
        "destination": trip.destination,
        "travel_date": trip.travel_date,
        "travel_time": trip.travel_time,
        "purpose": trip.purpose,
        "passengers": trip.passengers,
        "vehicle": trip.vehicle,
        "trip_type": trip.trip_type or "single",
        # ... other fields
    }
```

### 5. Database Models Update

#### A. Update Trip Model
```python
class Trip(Base):
    __tablename__ = "trips"
    
    id = Column(Integer, primary_key=True, index=True)
    destination = Column(String)
    purpose = Column(String)
    travel_date = Column(Date)
    travel_time = Column(Time)
    status = Column(String(20), default="pending", nullable=False)  # Add this
    pickup_location = Column(String)
    requested_by = Column(String)
    trip_type = Column(String, default="single")
    # ... other fields
    
    # Add constraint
    __table_args__ = (
        CheckConstraint(
            "status IN ('pending', 'on_route', 'arrived', 'returning', 'completed')",
            name='check_trip_status'
        ),
    )
```

#### B. Add Status History Model (Optional)
```python
class TripStatusHistory(Base):
    __tablename__ = "trip_status_history"
    
    id = Column(Integer, primary_key=True, index=True)
    trip_id = Column(Integer, ForeignKey("trips.id"), nullable=False)
    status = Column(String(20), nullable=False)
    changed_at = Column(DateTime, default=datetime.utcnow)
    changed_by = Column(Integer, ForeignKey("users.id"))
    
    # Relationships
    trip = relationship("Trip", back_populates="status_history")
    user = relationship("User")
```

### 6. Request/Response Models

#### A. Return Start Request
```python
class ReturnStartRequest(BaseModel):
    odometer_start: float
    fuel_start: float
    return_start_time: str
    return_start_location: str
```

#### B. Return Arrival Request
```python
class ReturnArrivalRequest(BaseModel):
    odometer_end: float
    fuel_end: float
    return_arrival_time: str
    return_arrival_location: str
    fuel_used: Optional[float] = None
    notes: Optional[str] = None
```

### 7. Error Handling

Add specific error responses for status-related issues:

```python
class InvalidStatusTransitionError(HTTPException):
    def __init__(self, current_status: str, new_status: str):
        super().__init__(
            status_code=400,
            detail={
                "error": "Invalid status transition",
                "current_status": current_status,
                "attempted_status": new_status,
                "message": f"Cannot transition from '{current_status}' to '{new_status}'"
            }
        )

class TripNotFoundError(HTTPException):
    def __init__(self, trip_id: int):
        super().__init__(
            status_code=404,
            detail={
                "error": "Trip not found",
                "trip_id": trip_id,
                "message": f"Trip with ID {trip_id} does not exist"
            }
        )
```

### 8. Testing

Add unit tests for status transitions:

```python
def test_status_transitions():
    # Test valid transitions
    assert validate_status_transition("pending", "on_route") == True
    assert validate_status_transition("on_route", "arrived") == True
    assert validate_status_transition("arrived", "returning") == True
    assert validate_status_transition("returning", "completed") == True
    assert validate_status_transition("arrived", "completed") == True  # Direct completion
    
    # Test invalid transitions
    assert validate_status_transition("pending", "arrived") == False
    assert validate_status_transition("on_route", "completed") == False
    assert validate_status_transition("completed", "pending") == False
    assert validate_status_transition("returning", "on_route") == False

def test_departure_endpoint():
    # Test successful departure
    response = client.post("/trips/1/departure", json={
        "odometer_start": 1000.0,
        "fuel_balance_start": 50.0
    })
    assert response.status_code == 200
    assert response.json()["status"] == "on_route"

def test_arrival_endpoint():
    # Test successful arrival
    response = client.post("/trips/1/arrival", json={
        "odometer_arrival": 1100.0,
        "fuel_end": 45.0,
        "fuel_purchased": 5.0,
        "fuel_used": 10.0
    })
    assert response.status_code == 200
    assert response.json()["status"] == "arrived"
```

## Implementation Checklist

- [ ] Add `status` column to `trips` table
- [ ] Create database migration script
- [ ] Update Trip model with status field and constraints
- [ ] Update `/trips/{trip_id}/departure` endpoint
- [ ] Update `/trips/{trip_id}/arrival` endpoint  
- [ ] Add `/trips/{trip_id}/return-start` endpoint
- [ ] Add `/trips/{trip_id}/return-arrival` endpoint
- [ ] Add status validation functions
- [ ] Update `/trips/{id}` endpoint to return status
- [ ] Add error handling for invalid transitions
- [ ] Update API documentation
- [ ] Add unit tests for status transitions
- [ ] Optional: Add status history tracking
- [ ] Test with frontend integration

## API Documentation Updates

Update your OpenAPI/Swagger documentation:

```yaml
# OpenAPI/Swagger documentation
TripDetails:
  type: object
  required:
    - id
    - status
    - travel_date
  properties:
    id:
      type: integer
      description: "Unique trip identifier"
    status:
      type: string
      enum: [pending, on_route, arrived, returning, completed]
      description: "Current status of the trip"
      example: "pending"
    destination:
      type: string
      description: "Trip destination"
    travel_date:
      type: string
      format: date
      description: "Date of travel"
    # ... other properties

# Add status transition documentation
TripStatusTransition:
  type: object
  properties:
    from_status:
      type: string
      enum: [pending, on_route, arrived, returning, completed]
    to_status:
      type: string
      enum: [pending, on_route, arrived, returning, completed]
    valid:
      type: boolean
      description: "Whether the transition is valid"
```

## Notes

1. **Backward Compatibility**: Existing trips without status will default to "pending"
2. **Flexibility**: The "arrived" status can transition directly to "completed" for trips that don't require return to base
3. **Audit Trail**: Optional status history table provides full audit trail of status changes
4. **Validation**: Status transitions are validated to prevent invalid state changes
5. **Error Handling**: Clear error messages help frontend handle invalid transitions gracefully

## Frontend Integration

The frontend expects:
- Trip details to include the `status` field
- Status to be updated after each action (departure, arrival, return start, return arrival)
- Clear error messages for invalid transitions
- Consistent status values: `pending`, `on_route`, `arrived`, `returning`, `completed`

This implementation ensures that single trips have the same robust status tracking as shared trips, enabling the new status-based UI to work correctly.
