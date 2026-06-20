# FoodDash Android

A comprehensive mobile food delivery application built with Android, part of the FoodDash ecosystem—a complete food delivery platform designed for restaurant management and customer ordering.

## Overview

FoodDash Android is the mobile client application for the FoodDash platform, enabling customers to browse restaurants, view menus, place orders, and track deliveries in real-time. The app integrates seamlessly with the FoodDash web system, which provides administrative and restaurant management interfaces.

## System Architecture

FoodDash is a full-stack food delivery solution consisting of:

- **FoodDash Android** (this repository) - Mobile app for customer ordering and tracking
- **FoodDash Web System** - Admin dashboard and restaurant management portal
- **Backend Services** - RESTful APIs for handling orders, authentication, and real-time updates

## Key Features

- **User Authentication** - Secure login and account management
- **Restaurant Browsing** - Explore available restaurants and menus
- **Order Management** - Place, track, and manage food orders
- **Real-time Notifications** - Order status updates and order history
- **Order Tracking** - Monitor delivery status in real-time
- **User Profiles** - Manage account information and preferences

## Technology Stack

### Mobile
- **Language**: Kotlin
- **Framework**: Android SDK
- **Build Tool**: Gradle
- **Architecture**: MVVM/MVC with Jetpack components

### Backend & Database
- **Database**: MySQL (via XAMPP)
- **Backend Server**: RESTful API
- **Integration**: RESTful HTTP requests

## Related Projects

- **Web System**: [FoodDash Web](https://github.com/powerrangerblue/FoodDash.git) - Admin dashboard and restaurant management portal

## Getting Started

### Prerequisites
- Android Studio (latest stable version)
- Android SDK 21+
- Gradle 8.0+
- Access to FoodDash backend API

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/codex-xx/FOODDASH-ANDROID.git
   ```

2. Open the project in Android Studio

3. Configure your backend API endpoint in the local properties or configuration files

4. Build and run the application on an emulator or device

## Building & Running

```bash
# Build the project
./gradlew build

# Build and run on a connected device
./gradlew installDebug
```

## Development Notes

- The app communicates with the FoodDash backend API for all data operations
- MySQL database (via XAMPP) serves as the central data store for the entire platform
- Ensure the backend API is running and accessible before launching the app

## Contributing

Contributions are welcome! Please follow the project's code style and submit pull requests with clear descriptions.

## License

This project is part of the FoodDash platform.
