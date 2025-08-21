# Advanced-Android-Navigation-SDK-Demo
Advanced Android Navigation SDK Demo
This project is an advanced demonstration application showcasing a rich set of features of the Google Navigation SDK for Android. It integrates various customization options, providing developers with a comprehensive example of how to build a feature-rich, customized navigation experience.

This application was developed with the assistance and iterative guidance of Google's AI.

🌟 Features
This demo application implements the following features, offering a complete navigation workflow from destination selection to arrival:

📍 Origin & Destination Selection:

Utilizes the Places SDK for Android for a seamless address search experience with Autocomplete.
Allows users to select a specific address for both origin and destination.
Supports setting the current GPS location as the starting point.
Includes a swap button to easily interchange the origin and destination.
🗺️ Route & Navigation Control:

Start/Stop Navigation: Full control over the guidance lifecycle.
Simulated Navigation: Start and stop route simulation with an adjustable speed multiplier, perfect for testing and debugging.
🎨 UI & Map Customization:

Custom Vehicle Icon:
Replaces the default blue chevron with a custom icon.
Provides a selection dialog to choose from multiple vehicle icons (e.g., yellow car, orange truck).
The custom icon correctly follows the vehicle's position and bearing during both real and simulated navigation.
Custom Route Color:
Allows users to change the color of the upcoming route polyline via a selection dialog.
The custom-colored route dynamically updates (shortens) as the vehicle travels.
Supports reverting to the default SDK-rendered blue route.
Traffic Layer Toggle: Show or hide the real-time traffic layer on the map.
Traffic Lights & Stop Signs Toggle: Dynamically show or hide traffic lights and stop signs along the route during navigation.
Night Mode Control: A selection dialog allows users to switch between Auto, Day, and Night modes.
⚙️ Built-in Navigation UI Toggles:

Speedometer: Show or hide the speed limit and current speed display.
Trip Progress Bar: Show or hide the vertical bar indicating overall trip progress.
Voice Guidance: Mute or unmute turn-by-turn voice instructions.
Camera Perspective: Switch between a tilted, following perspective and a top-down overview of the entire route.
🛠️ Robust Architecture:

Stable Location Handling: Implements a hybrid location strategy, using FusedLocationProviderClient for idle state and Navigator's internal listeners during navigation to ensure smooth and accurate icon movement in all scenarios (including simulation).
Performance Optimized: Avoids common pitfalls like Application Not Responding (ANR) errors by managing UI updates on the main thread correctly.
Clean UI Layout: Utilizes NavigationView's setCustomControl with a FOOTER position to ensure control buttons are always visible and do not overlap with the Google logo or other essential UI elements.
📸 Screenshots
<img width="1280" height="2856" alt="image" src="https://github.com/user-attachments/assets/c1b398eb-cea9-475b-b24e-b0383f1f19b4" />



Idle State	Navigation with Custom Icon
![Screenshot of the app in its idle state, showing the origin/destination input panel.]<img width="1280" height="2856" alt="image" src="https://github.com/user-attachments/assets/74a669b1-aa30-4083-ad49-7e411d51ebb8" />

<img width="1280" height="2856" alt="image" src="https://github.com/user-attachments/assets/0391f0c9-a757-4120-b1ce-8c7415637f8e" />

Color & Icon Selection
![Screenshot of the dialog for selecting vehicle icons and route colors.]<img width="1280" height="2856" alt="image" src="https://github.com/user-attachments/assets/13784a87-3549-42b7-b529-f8791bdddca3" />

🚀 Getting Started
Prerequisites
Android Studio
A Google Maps Platform project with the following APIs enabled:
Navigation SDK for Android
Maps SDK for Android
Places API
Routes API
An API Key stored securely. This project expects the key in the local.defaults.properties file: MAPS_API_KEY=YOUR_API_KEY_HERE
Installation
Clone the repository:
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY_NAME.git
Create your local.defaults.properties file in the project's root directory.
Add your API key to the file:
MAPS_API_KEY=AIzaSy...
Open the project in Android Studio, let Gradle sync, and run the app on a device or emulator.
🔧 Key Implementation Details
This project serves as a practical guide for several advanced Navigation SDK topics:

Customizing the Vehicle Marker: The core challenge of replacing the default navigation icon is solved by disabling the default MyLocation layer (map.isMyLocationEnabled = false) and rendering a custom Marker driven by a hybrid location strategy. This ensures the icon works seamlessly in both real and simulated navigation.
Dynamic Route Styling: Instead of a static overlay, the route color is changed by re-drawing the Polyline based on the latest navigator.getRouteSegments() data within a high-frequency listener. This simulates the "route being consumed" effect.
Safe UI Customization: The control panel is injected into the CustomControlPosition.FOOTER area, the official method推奨 to avoid UI overlap with SDK elements.
State Management: The app carefully manages various boolean states (isSimulating, isOriginMyLocation, etc.) to provide a smooth and logical user experience.
📄 License
This project is licensed under the Apache 2.0 License. See the LICENSE file for details.

