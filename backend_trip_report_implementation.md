# Backend Trip Report HTML Implementation

## 1. HTML Template (`trip_report_template.html`)

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Trip Ticket Log - {{trip_id}}</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            line-height: 1.6;
            color: #333;
            background-color: #f8f9fa;
            padding: 20px;
        }
        
        .container {
            max-width: 1000px;
            margin: 0 auto;
            background: white;
            border-radius: 10px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            overflow: hidden;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            text-align: center;
        }
        
        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            font-weight: 300;
        }
        
        .header .subtitle {
            font-size: 1.2em;
            opacity: 0.9;
        }
        
        .trip-info {
            padding: 30px;
            border-bottom: 2px solid #eee;
        }
        
        .info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        
        .info-item {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 8px;
            border-left: 4px solid #667eea;
        }
        
        .info-label {
            font-weight: 600;
            color: #667eea;
            font-size: 0.9em;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .info-value {
            font-size: 1.1em;
            margin-top: 5px;
        }
        
        .leg-section {
            padding: 30px;
            border-bottom: 1px solid #eee;
        }
        
        .leg-section:last-child {
            border-bottom: none;
        }
        
        .leg-header {
            display: flex;
            align-items: center;
            margin-bottom: 25px;
        }
        
        .leg-icon {
            width: 50px;
            height: 50px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.5em;
            color: white;
            margin-right: 20px;
            font-weight: bold;
        }
        
        .leg-icon.departure { background: #28a745; }
        .leg-icon.arrival { background: #007bff; }
        .leg-icon.return { background: #ffc107; color: #333; }
        .leg-icon.final { background: #dc3545; }
        
        .leg-title {
            font-size: 1.4em;
            font-weight: 600;
            color: #333;
        }
        
        .leg-subtitle {
            color: #666;
            font-size: 0.9em;
            margin-top: 5px;
        }
        
        .details-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
            background: white;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        
        .details-table th {
            background: #667eea;
            color: white;
            padding: 15px;
            text-align: left;
            font-weight: 600;
        }
        
        .details-table td {
            padding: 15px;
            border-bottom: 1px solid #eee;
        }
        
        .details-table tr:last-child td {
            border-bottom: none;
        }
        
        .details-table tr:nth-child(even) {
            background: #f8f9fa;
        }
        
        .summary-section {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
        }
        
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
        }
        
        .summary-item {
            text-align: center;
            background: rgba(255,255,255,0.1);
            padding: 20px;
            border-radius: 8px;
            backdrop-filter: blur(10px);
        }
        
        .summary-value {
            font-size: 2em;
            font-weight: bold;
            margin-bottom: 5px;
        }
        
        .summary-label {
            font-size: 0.9em;
            opacity: 0.9;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .status-badge {
            display: inline-block;
            padding: 5px 12px;
            border-radius: 20px;
            font-size: 0.8em;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .status-completed {
            background: #d4edda;
            color: #155724;
        }
        
        .status-in-progress {
            background: #cce5ff;
            color: #004085;
        }
        
        .status-pending {
            background: #fff3cd;
            color: #856404;
        }
        
        .route-indicator {
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 20px 0;
            color: #667eea;
            font-weight: 600;
        }
        
        .route-arrow {
            margin: 0 10px;
            font-size: 1.2em;
        }
        
        @media print {
            body {
                background: white;
                padding: 0;
            }
            
            .container {
                box-shadow: none;
                border-radius: 0;
            }
            
            .header {
                background: #667eea !important;
                -webkit-print-color-adjust: exact;
                print-color-adjust: exact;
            }
            
            .summary-section {
                background: #667eea !important;
                -webkit-print-color-adjust: exact;
                print-color-adjust: exact;
            }
        }
        
        .print-button {
            position: fixed;
            top: 20px;
            right: 20px;
            background: #667eea;
            color: white;
            border: none;
            padding: 12px 20px;
            border-radius: 25px;
            cursor: pointer;
            font-weight: 600;
            box-shadow: 0 2px 10px rgba(0,0,0,0.2);
            transition: all 0.3s ease;
        }
        
        .print-button:hover {
            background: #5a6fd8;
            transform: translateY(-2px);
        }
    </style>
</head>
<body>
    <button class="print-button" onclick="window.print()">🖨️ Print Report</button>
    
    <div class="container">
        <!-- Header -->
        <div class="header">
            <h1>🚐 Trip Ticket Log</h1>
            <div class="subtitle">DriveBroom Transportation Management System</div>
        </div>
        
        <!-- Trip Information -->
        <div class="trip-info">
            <div class="info-grid">
                <div class="info-item">
                    <div class="info-label">Driver</div>
                    <div class="info-value">{{driver_name}}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Vehicle</div>
                    <div class="info-value">{{vehicle_model}} ({{vehicle_plate}})</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Date</div>
                    <div class="info-value">{{trip_date}}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Trip Type</div>
                    <div class="info-value">{{trip_type}}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Trip ID</div>
                    <div class="info-value">#{{trip_id}}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Status</div>
                    <div class="info-value">
                        <span class="status-badge status-{{trip_status}}">{{trip_status}}</span>
                    </div>
                </div>
            </div>
            
            <div class="route-indicator">
                {{route_summary}}
            </div>
        </div>
        
        <!-- Leg Sections -->
        {{#each legs}}
        <div class="leg-section">
            <div class="leg-header">
                <div class="leg-icon {{leg_type}}">{{leg_icon}}</div>
                <div>
                    <div class="leg-title">{{leg_title}}</div>
                    <div class="leg-subtitle">{{leg_subtitle}}</div>
                </div>
            </div>
            
            <table class="details-table">
                <thead>
                    <tr>
                        <th>Details</th>
                        <th>Reading / Info</th>
                    </tr>
                </thead>
                <tbody>
                    {{#if departure_data}}
                    <tr>
                        <td><strong>Odometer Start</strong></td>
                        <td>{{departure_data.odometer_start}} km</td>
                    </tr>
                    <tr>
                        <td><strong>Fuel Level Start</strong></td>
                        <td>{{departure_data.fuel_start}} L</td>
                    </tr>
                    <tr>
                        <td><strong>Departure Time</strong></td>
                        <td>{{departure_data.departure_time}}</td>
                    </tr>
                    <tr>
                        <td><strong>Departure Location</strong></td>
                        <td>{{departure_data.departure_location}}</td>
                    </tr>
                    {{/if}}
                    
                    {{#if arrival_data}}
                    <tr>
                        <td><strong>Odometer {{#if is_return}}End{{else}}Arrival{{/if}}</strong></td>
                        <td>{{arrival_data.odometer_end}} km</td>
                    </tr>
                    <tr>
                        <td><strong>Fuel Level {{#if is_return}}End{{else}}</strong></td>
                        <td>{{arrival_data.fuel_end}} L</td>
                    </tr>
                    <tr>
                        <td><strong>{{#if is_return}}Arrival{{else}}Arrival{{/if}} Time</strong></td>
                        <td>{{arrival_data.arrival_time}}</td>
                    </tr>
                    <tr>
                        <td><strong>{{#if is_return}}Final{{else}}Arrival{{/if}} Location</strong></td>
                        <td>{{arrival_data.arrival_location}}</td>
                    </tr>
                    {{/if}}
                    
                    {{#if passengers}}
                    <tr>
                        <td><strong>Passengers</strong></td>
                        <td>{{passengers}}</td>
                    </tr>
                    {{/if}}
                    
                    {{#if fuel_purchased}}
                    <tr>
                        <td><strong>Fuel Purchased</strong></td>
                        <td>{{fuel_purchased}} L</td>
                    </tr>
                    {{/if}}
                    
                    {{#if notes}}
                    <tr>
                        <td><strong>Remarks</strong></td>
                        <td>{{notes}}</td>
                    </tr>
                    {{/if}}
                </tbody>
            </table>
        </div>
        {{/each}}
        
        <!-- Trip Summary -->
        <div class="summary-section">
            <h2 style="text-align: center; margin-bottom: 30px; font-size: 2em;">📊 Trip Summary</h2>
            <div class="summary-grid">
                <div class="summary-item">
                    <div class="summary-value">{{total_distance}} km</div>
                    <div class="summary-label">Total Distance</div>
                </div>
                <div class="summary-item">
                    <div class="summary-value">{{total_fuel_used}} L</div>
                    <div class="summary-label">Total Fuel Used</div>
                </div>
                <div class="summary-item">
                    <div class="summary-value">{{fuel_efficiency}} km/L</div>
                    <div class="summary-label">Fuel Efficiency</div>
                </div>
                <div class="summary-item">
                    <div class="summary-value">{{total_legs}}</div>
                    <div class="summary-label">Total Legs</div>
                </div>
                <div class="summary-item">
                    <div class="summary-value">{{total_passengers}}</div>
                    <div class="summary-label">Total Passengers</div>
                </div>
                <div class="summary-item">
                    <div class="summary-value">{{trip_duration}}</div>
                    <div class="summary-label">Trip Duration</div>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        // Auto-refresh functionality
        function refreshData() {
            fetch(`/api/trips/{{trip_id}}/report`)
                .then(response => response.json())
                .then(data => {
                    location.reload();
                })
                .catch(error => console.error('Error refreshing data:', error));
        }
        
        // Auto-refresh every 30 seconds if trip is in progress
        {{#if is_in_progress}}
        setInterval(refreshData, 30000);
        {{/if}}
        
        // Print functionality
        window.addEventListener('beforeprint', function() {
            document.querySelector('.print-button').style.display = 'none';
        });
        
        window.addEventListener('afterprint', function() {
            document.querySelector('.print-button').style.display = 'block';
        });
    </script>
</body>
</html>
```

## 2. PHP Backend Controller (`TripReportController.php`)

```php
<?php

class TripReportController {
    
    public function generateTripReport($tripId) {
        // Get trip data from database
        $tripData = $this->getTripData($tripId);
        $legsData = $this->getLegsData($tripId);
        
        // Calculate summary data
        $summaryData = $this->calculateSummary($legsData);
        
        // Generate HTML content
        $htmlContent = $this->generateHtml($tripData, $legsData, $summaryData);
        
        return $htmlContent;
    }
    
    private function getTripData($tripId) {
        // Query your database for trip information
        $query = "
            SELECT 
                t.id,
                t.status,
                t.travel_date,
                t.travel_time,
                t.trip_type,
                d.name as driver_name,
                v.model as vehicle_model,
                v.plate_number as vehicle_plate
            FROM trips t
            JOIN drivers d ON t.driver_id = d.id
            JOIN vehicles v ON t.vehicle_id = v.id
            WHERE t.id = ?
        ";
        
        // Execute query and return result
        // This is pseudo-code - adapt to your database implementation
        return $this->db->fetchOne($query, [$tripId]);
    }
    
    private function getLegsData($tripId) {
        $query = "
            SELECT 
                leg_id,
                stop_id,
                team_name,
                destination,
                passengers,
                odometer_start,
                odometer_end,
                fuel_start,
                fuel_end,
                fuel_used,
                fuel_purchased,
                departure_time,
                arrival_time,
                departure_location,
                arrival_location,
                status,
                return_to_base,
                notes
            FROM shared_trip_legs 
            WHERE trip_id = ? 
            ORDER BY leg_id
        ";
        
        return $this->db->fetchAll($query, [$tripId]);
    }
    
    private function calculateSummary($legsData) {
        $totalDistance = 0;
        $totalFuelUsed = 0;
        $totalFuelPurchased = 0;
        $totalPassengers = 0;
        
        foreach ($legsData as $leg) {
            if ($leg['odometer_start'] && $leg['odometer_end']) {
                $totalDistance += ($leg['odometer_end'] - $leg['odometer_start']);
            }
            if ($leg['fuel_used']) {
                $totalFuelUsed += $leg['fuel_used'];
            }
            if ($leg['fuel_purchased']) {
                $totalFuelPurchased += $leg['fuel_purchased'];
            }
            if ($leg['passengers']) {
                $passengers = json_decode($leg['passengers'], true);
                $totalPassengers += count($passengers);
            }
        }
        
        $fuelEfficiency = $totalDistance > 0 && $totalFuelUsed > 0 ? 
            round($totalDistance / $totalFuelUsed, 2) : 0;
        
        return [
            'total_distance' => $totalDistance,
            'total_fuel_used' => $totalFuelUsed,
            'total_fuel_purchased' => $totalFuelPurchased,
            'fuel_efficiency' => $fuelEfficiency,
            'total_legs' => count($legsData),
            'total_passengers' => $totalPassengers
        ];
    }
    
    private function generateHtml($tripData, $legsData, $summaryData) {
        // Load HTML template
        $template = file_get_contents('trip_report_template.html');
        
        // Replace template variables
        $html = str_replace('{{trip_id}}', $tripData['id'], $template);
        $html = str_replace('{{driver_name}}', $tripData['driver_name'], $html);
        $html = str_replace('{{vehicle_model}}', $tripData['vehicle_model'], $html);
        $html = str_replace('{{vehicle_plate}}', $tripData['vehicle_plate'], $html);
        $html = str_replace('{{trip_date}}', date('F j, Y', strtotime($tripData['travel_date'])), $html);
        $html = str_replace('{{trip_type}}', ucfirst($tripData['trip_type']), $html);
        $html = str_replace('{{trip_status}}', $tripData['status'], $html);
        $html = str_replace('{{total_distance}}', $summaryData['total_distance'], $html);
        $html = str_replace('{{total_fuel_used}}', $summaryData['total_fuel_used'], $html);
        $html = str_replace('{{fuel_efficiency}}', $summaryData['fuel_efficiency'], $html);
        $html = str_replace('{{total_legs}}', $summaryData['total_legs'], $html);
        $html = str_replace('{{total_passengers}}', $summaryData['total_passengers'], $html);
        
        // Generate route summary
        $routeSummary = $this->generateRouteSummary($legsData);
        $html = str_replace('{{route_summary}}', $routeSummary, $html);
        
        // Generate legs HTML
        $legsHtml = $this->generateLegsHtml($legsData);
        $html = str_replace('{{#each legs}}', '', $html);
        $html = str_replace('{{/each}}', $legsHtml, $html);
        
        return $html;
    }
    
    private function generateRouteSummary($legsData) {
        $locations = [];
        foreach ($legsData as $leg) {
            $locations[] = $leg['destination'];
        }
        return implode(' → ', $locations);
    }
    
    private function generateLegsHtml($legsData) {
        $html = '';
        
        foreach ($legsData as $index => $leg) {
            $legNumber = $index + 1;
            $isReturn = $leg['return_to_base'];
            
            $html .= '<div class="leg-section">';
            $html .= '<div class="leg-header">';
            
            // Leg icon and title based on type
            if ($leg['status'] === 'completed' && $isReturn) {
                $html .= '<div class="leg-icon return">🔄</div>';
                $html .= '<div class="leg-title">Return to Base - Leg ' . $legNumber . '</div>';
                $html .= '<div class="leg-subtitle">Return journey to ISATU Miagao Campus</div>';
            } else {
                $html .= '<div class="leg-icon departure">🚌</div>';
                $html .= '<div class="leg-title">Leg ' . $legNumber . ' - ' . $leg['destination'] . '</div>';
                $html .= '<div class="leg-subtitle">Team: ' . $leg['team_name'] . '</div>';
            }
            
            $html .= '</div>';
            
            // Details table
            $html .= '<table class="details-table">';
            $html .= '<thead><tr><th>Details</th><th>Reading / Info</th></tr></thead>';
            $html .= '<tbody>';
            
            // Departure data
            if ($leg['odometer_start'] && $leg['departure_time']) {
                $html .= '<tr><td><strong>Odometer Start</strong></td><td>' . $leg['odometer_start'] . ' km</td></tr>';
                $html .= '<tr><td><strong>Fuel Level Start</strong></td><td>' . $leg['fuel_start'] . ' L</td></tr>';
                $html .= '<tr><td><strong>Departure Time</strong></td><td>' . date('g:i A', strtotime($leg['departure_time'])) . '</td></tr>';
                $html .= '<tr><td><strong>Departure Location</strong></td><td>' . $leg['departure_location'] . '</td></tr>';
            }
            
            // Arrival data
            if ($leg['odometer_end'] && $leg['arrival_time']) {
                $html .= '<tr><td><strong>Odometer ' . ($isReturn ? 'End' : 'Arrival') . '</strong></td><td>' . $leg['odometer_end'] . ' km</td></tr>';
                $html .= '<tr><td><strong>Fuel Level ' . ($isReturn ? 'End' : '') . '</strong></td><td>' . $leg['fuel_end'] . ' L</td></tr>';
                $html .= '<tr><td><strong>Arrival Time</strong></td><td>' . date('g:i A', strtotime($leg['arrival_time'])) . '</td></tr>';
                $html .= '<tr><td><strong>' . ($isReturn ? 'Final' : 'Arrival') . ' Location</strong></td><td>' . $leg['arrival_location'] . '</td></tr>';
            }
            
            // Passengers
            if ($leg['passengers']) {
                $passengers = json_decode($leg['passengers'], true);
                $html .= '<tr><td><strong>Passengers</strong></td><td>' . implode(', ', $passengers) . '</td></tr>';
            }
            
            // Fuel purchased
            if ($leg['fuel_purchased']) {
                $html .= '<tr><td><strong>Fuel Purchased</strong></td><td>' . $leg['fuel_purchased'] . ' L</td></tr>';
            }
            
            // Notes
            if ($leg['notes']) {
                $html .= '<tr><td><strong>Remarks</strong></td><td>' . $leg['notes'] . '</td></tr>';
            }
            
            $html .= '</tbody></table>';
            $html .= '</div>';
        }
        
        return $html;
    }
}

// API Route handler
function handleTripReport($tripId) {
    $controller = new TripReportController();
    $html = $controller->generateTripReport($tripId);
    
    // Set headers for HTML response
    header('Content-Type: text/html; charset=UTF-8');
    header('Cache-Control: no-cache, must-revalidate');
    
    echo $html;
}

// Usage: GET /api/trips/{id}/report
// Example: GET /api/trips/123/report
```

## 3. API Route Implementation

```php
// Add this to your routes file (e.g., routes/api.php)

Route::get('/trips/{id}/report', function($id) {
    $controller = new TripReportController();
    return $controller->generateTripReport($id);
});

// Or with authentication middleware
Route::middleware('auth:api')->get('/trips/{id}/report', function($id) {
    $controller = new TripReportController();
    return $controller->generateTripReport($id);
});
```

## 4. Database Schema Updates

```sql
-- Add these columns to your shared_trip_legs table
ALTER TABLE shared_trip_legs ADD COLUMN return_to_base BOOLEAN DEFAULT FALSE;
ALTER TABLE shared_trip_legs ADD COLUMN departure_location VARCHAR(255);
ALTER TABLE shared_trip_legs ADD COLUMN arrival_location VARCHAR(255);
ALTER TABLE shared_trip_legs ADD COLUMN notes TEXT;

-- Update status enum
ALTER TABLE shared_trip_legs MODIFY COLUMN status ENUM('pending', 'approved', 'on_route', 'arrived', 'returning', 'completed');
```

## 5. Usage Examples

### Generate Report URL
```
GET http://192.168.254.132:8000/api/trips/123/report
```

### Expected Output
The HTML will generate a beautiful, printable trip report that matches your example with:
- ✅ Trip header with driver, vehicle, date info
- ✅ Route summary (Iloilo City → Miagao → Iloilo City → San Joaquin)
- ✅ Detailed leg sections with odometer, fuel, time data
- ✅ Trip summary with totals and efficiency calculations
- ✅ Print-friendly styling
- ✅ Real-time refresh for in-progress trips

This implementation will generate exactly the type of trip ticket log you showed in your example!
