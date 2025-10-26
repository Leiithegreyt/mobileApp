# DriveBroom Mobile Application - Complete User Flow Documentation

## Overview

The DriveBroom mobile application is a comprehensive driver management system designed for ISATU Miagao Campus. The application provides drivers with secure authentication, real-time trip scheduling, detailed trip execution workflows, and comprehensive trip logging capabilities. The system supports both single trips and complex shared trips with multiple legs, ensuring accurate operational data collection and seamless communication between drivers, administrators, and requestors.

## Application Architecture

The application follows a modern Android architecture using Jetpack Compose for UI, MVVM pattern with ViewModels, and Firebase for push notifications. The system integrates with a backend API for real-time data synchronization and supports both online and offline capabilities for critical trip operations.

---

## 1. Authentication and Login System

### Figure 1. Driver Login Screen

The application begins with a secure authentication system that ensures only authorized drivers can access the platform. The login screen features a clean, professional interface with email and password fields, providing immediate visual feedback for validation errors and loading states. When drivers enter their credentials, the system validates them against the backend database and establishes a secure session using JWT tokens.

The login process includes comprehensive error handling, displaying specific error messages for invalid credentials, network connectivity issues, or server problems. Once authenticated, the system automatically redirects drivers to their personalized dashboard, and if they received a push notification with a specific trip ID, the application intelligently navigates directly to that trip's details page.

**Key Features:**
- Secure credential validation
- Real-time error feedback
- Automatic session management
- Deep linking support for notifications
- Loading state indicators

---

## 2. Driver Dashboard - Trip Management Hub

### Figure 2. Driver Home Dashboard

The driver dashboard serves as the central command center where drivers can view their daily schedule, access upcoming trips, and manage their workflow efficiently. The interface displays the driver's name prominently in the top app bar, along with quick access to their profile information and logout functionality.

The dashboard is intelligently organized into two main sections: "Today's Schedule" showing all trips scheduled for the current day, and a "Next Schedule" button that reveals upcoming trips beyond today. Each trip is presented as an interactive card displaying essential information including trip number, status, date, time, destination, and the person who requested the trip.

**Smart Features:**
- Real-time trip status updates
- Automatic filtering of completed trips
- Visual status indicators with color-coded chips
- Shared trip identification with special badges
- Pull-to-refresh functionality for latest updates

### Figure 3. Trip Card Details

Each trip card provides comprehensive information at a glance, enabling drivers to quickly understand their assignments. The cards feature a status chip that dynamically updates based on the trip's current state (pending, approved, in progress, completed), making it easy for drivers to prioritize their tasks. The trip number is prominently displayed for easy reference, while the date and time information helps drivers plan their day effectively.

For shared trips, a distinctive "Shared" badge appears, alerting drivers to the multi-leg nature of these assignments. The destination and requester information provides context for each trip, helping drivers prepare appropriately for their assignments.

---

## 3. Schedule Management

### Figure 4. Next Schedule View

The Next Schedule functionality allows drivers to view and plan for upcoming trips beyond the current day. This feature is particularly valuable for drivers who need to prepare for multi-day assignments or coordinate with other commitments. The interface maintains the same intuitive card-based design as the main dashboard, ensuring consistency in user experience.

Drivers can easily navigate between today's schedule and future schedules using the dedicated navigation controls. The system automatically filters and sorts trips chronologically, helping drivers understand their upcoming workload and plan accordingly.

**Planning Benefits:**
- Forward-looking trip visibility
- Chronological organization
- Consistent interface design
- Easy navigation between time periods

---

## 4. Notification System

### Figure 5. Push Notification for New Trip Assignment

The application implements a comprehensive notification system that ensures drivers never miss important updates or new assignments. Push notifications are delivered through Firebase Cloud Messaging, providing instant alerts even when the application is not actively running. When a new trip is assigned, drivers receive a notification with the trip details and destination.

The notification system is intelligent and context-aware. If a driver taps on a notification while the app is closed, the system will authenticate the user and then navigate directly to the specific trip's details page. This seamless integration between notifications and the application workflow ensures maximum efficiency and reduces the time between assignment and action.

**Notification Features:**
- Real-time push notifications
- Deep linking to specific trips
- In-app notification management
- Automatic trip assignment alerts
- Status update notifications

---

## 5. Single Trip Execution Workflow

### Figure 6. Trip Details - Pre-Execution View

When drivers select a trip from their dashboard, they are presented with a comprehensive trip details screen that provides all necessary information for successful trip execution. The interface displays the trip number, assigned vehicle, destination, purpose, and scheduled date/time. The screen also shows all authorized passengers as interactive chips, allowing drivers to confirm passenger attendance during the trip.

The trip details screen adapts dynamically based on the trip's current status, showing appropriate action buttons that guide drivers through the execution process. For trips that haven't been started, drivers see a "Depart" button, while trips in progress show "Arrived" or "Return to Base" options based on their current state.

### Figure 7. Departure Recording Dialog

The departure process begins with a detailed dialog that captures essential operational data. Drivers must enter their current odometer reading and fuel level, which serves as the baseline for calculating distance traveled and fuel consumption throughout the trip. The system automatically records the departure time and pre-fills the departure location as "ISATU Miagao Campus."

For trips with multiple passengers, the dialog includes checkboxes for each authorized passenger, allowing drivers to confirm who is actually present for the trip. This passenger confirmation system ensures accurate record-keeping and helps with accountability and safety protocols.

**Data Collection:**
- Odometer start reading
- Fuel level at departure
- Departure time (automatic)
- Departure location (pre-filled)
- Passenger confirmation
- Vehicle status verification

### Figure 8. Arrival Recording Dialog

Upon reaching the destination, drivers use the arrival dialog to record their arrival data and calculate fuel consumption. The system requires the current odometer reading and fuel level, then automatically calculates the fuel used during the trip based on the departure fuel level and any fuel purchased during the journey.

The arrival dialog includes fields for fuel purchased (if any) and optional notes, providing a complete picture of the trip's operational aspects. The destination is automatically populated from the trip details, and the arrival time is recorded automatically. This comprehensive data collection ensures accurate reporting and helps with vehicle maintenance scheduling and fuel management.

**Operational Metrics:**
- Odometer end reading
- Fuel level at arrival
- Fuel purchased during trip
- Calculated fuel consumption
- Arrival time (automatic)
- Trip notes and observations

### Figure 9. Trip Itinerary Display

As drivers progress through the trip execution process, the system builds a detailed itinerary that tracks every movement and operational metric. The itinerary table displays departure and arrival information in a clear, organized format, showing odometer readings, times, and locations for each segment of the trip.

This real-time itinerary serves multiple purposes: it provides drivers with a clear record of their progress, enables administrators to track trip execution in real-time, and creates a comprehensive audit trail for operational analysis and reporting.

### Figure 10. Return Journey Initiation

For trips that require returning to the base, the system provides a streamlined return journey workflow. The return start dialog automatically pre-fills with the arrival odometer and fuel readings, ensuring continuity in the operational data chain. Drivers can adjust these values if needed and then initiate their return journey.

The return journey tracking is essential for complete trip documentation, especially for longer assignments or trips that involve multiple stops. This feature ensures that all vehicle movements are properly recorded and that fuel consumption and distance calculations are accurate for the entire trip duration.

### Figure 11. Base Arrival and Trip Completion

When drivers return to the base, they complete the trip by recording their final odometer reading and fuel level. The system uses this data to calculate the total distance traveled and total fuel consumption for the entire trip, providing comprehensive operational metrics.

The completion process includes a summary dialog that displays key trip statistics, including total distance traveled, fuel consumption, passenger count, and trip duration. This summary serves as a final verification for drivers and provides valuable data for administrative reporting and vehicle maintenance scheduling.

### Figure 12. Trip Completion Summary

The trip completion summary provides a comprehensive overview of the entire trip, including all operational data, passenger information, and timing details. This summary is automatically generated and can be used for reporting, billing, and operational analysis purposes.

The completion process triggers automatic updates to the trip status in the backend system, notifies relevant administrators and requestors of the trip's completion, and updates the driver's schedule to reflect the completed assignment.

---

## 6. Shared Trip Management

### Figure 13. Shared Trip Overview

Shared trips represent a more complex workflow where multiple passengers or destinations are involved in a single vehicle assignment. The shared trip interface provides drivers with a clear overview of all trip legs, showing the status of each segment and allowing drivers to work on individual legs as needed.

The shared trip system maintains the same operational data collection standards as single trips but organizes the information by leg, making it easier for drivers to manage complex multi-stop assignments. Each leg can be executed independently, with the system tracking progress across all segments.

### Figure 14. Shared Trip Leg Execution

Individual leg execution follows the same detailed workflow as single trips, with drivers recording departure and arrival data for each segment. The system maintains separate itineraries for each leg while providing an overall trip view that shows the complete journey from start to finish.

This granular approach to trip execution ensures that even complex shared trips are properly documented and that all operational metrics are accurately captured for each segment of the journey.

---

## 7. Trip Logs and Historical Data

### Figure 15. Trip Logs Dashboard

The trip logs system provides drivers with comprehensive access to their completed trip history. This feature is essential for drivers who need to review past assignments, verify completed work, or access historical data for reporting purposes.

The logs interface displays completed trips in a chronological format, with each entry showing key information such as trip number, destination, date, and completion status. Drivers can tap on any completed trip to view detailed information about the execution, including all operational data and itinerary details.

### Figure 16. Completed Trip Details

When drivers select a completed trip from the logs, they can view comprehensive details about the trip execution, including the complete itinerary, fuel consumption data, passenger information, and any notes or observations recorded during the trip.

This detailed view is particularly valuable for drivers who need to reference past trips for administrative purposes, dispute resolution, or performance analysis. The system maintains a complete audit trail of all trip activities, ensuring transparency and accountability.

### Figure 17. Shared Trip Historical View

For completed shared trips, the system provides a specialized view that shows the complete multi-leg journey with all operational data organized by leg. This view helps drivers and administrators understand the complexity of shared trips and verify that all segments were properly executed.

The shared trip historical view maintains the same level of detail as single trip logs but organizes the information in a way that clearly shows the relationship between different legs and the overall trip structure.

---

## 8. Profile Management and System Settings

### Figure 18. Driver Profile Dialog

The driver profile system provides access to personal information and account settings. Drivers can view their name, email address, and phone number, ensuring they have access to their account information when needed.

The profile system is designed for viewing rather than editing, with any profile modifications typically handled through administrative channels. This approach ensures data consistency and security while providing drivers with access to their account information.

### Figure 19. Logout Confirmation

The logout system includes a confirmation dialog to prevent accidental logouts, which could disrupt ongoing trip execution or cause data loss. When drivers choose to logout, the system confirms their intention and then securely terminates their session.

The logout process includes proper cleanup of authentication tokens and session data, ensuring that the driver's account remains secure and that any sensitive information is properly cleared from the device.

---

## 9. System Integration and Data Synchronization

### Real-Time Updates

The application maintains real-time synchronization with the backend system, ensuring that trip assignments, status updates, and operational data are immediately reflected across all connected devices and administrative interfaces.

### Offline Capability

Critical trip execution functions are designed to work even with limited connectivity, allowing drivers to continue recording operational data even in areas with poor network coverage. Data is synchronized when connectivity is restored.

### Error Handling

The system includes comprehensive error handling for network issues, server problems, and data validation errors, ensuring that drivers can continue their work even when technical issues occur.

---

## 10. Security and Compliance

### Authentication Security

The application uses industry-standard authentication protocols, including JWT tokens and secure session management, to protect driver accounts and sensitive operational data.

### Data Protection

All operational data, including odometer readings, fuel consumption, and passenger information, is encrypted during transmission and storage, ensuring compliance with data protection regulations and institutional policies.

### Audit Trail

The system maintains comprehensive audit trails for all trip activities, providing administrators with complete visibility into driver actions and operational metrics for compliance and reporting purposes.

---

## Conclusion

The DriveBroom mobile application provides a comprehensive solution for driver management and trip execution at ISATU Miagao Campus. Through its intuitive interface, real-time synchronization, and detailed operational tracking, the system ensures efficient trip management while maintaining accurate records for administrative and compliance purposes.

The application's modular design supports both simple single trips and complex shared trip scenarios, making it suitable for a wide range of transportation needs. The integration of push notifications, offline capabilities, and comprehensive logging ensures that drivers can effectively manage their assignments while providing administrators with the data they need for effective fleet management.

This system represents a significant advancement in campus transportation management, providing transparency, accountability, and efficiency in driver operations while maintaining the flexibility needed to adapt to changing transportation requirements.
