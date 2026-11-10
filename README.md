 🌐 VibeCircle
Find Your People. Share Your Interests. Build Your Circle.

**VibeCircle** is an interest-based social networking platform designed to help people discover, connect and interact with like-minded individuals within their city.

Unlike traditional social networking platforms that primarily revolve around existing connections, VibeCircle focuses on **interest-based discovery**, allowing users to expand their social circles through shared hobbies, activities and preferences.

The platform combines personalized user discovery, social content sharing, friendship management, privacy controls and real-time communication in one application.
---
## ✨ Key Features
### 👤 User Authentication & Profile Management
- Secure user registration and login.
- JWT-based authentication.
- User profile creation and management.
- Profile picture and personal information management.
- Interest and hobby selection.
- City-based profile information.

### 🔍 Interest-Based User Discovery
- Discover people belonging to the same city.
- Find users with similar hobbies and interests.
- Interest similarity calculation using the Jaccard similarity coefficient.
- Personalized user recommendations.
- Shared-interest visibility.

### 🤝 Friendship Management
- Send and receive friend requests.
- Accept and reject requests.
- Manage existing friendships.
- Prevent duplicate friendship requests.
- Block unwanted users.

### 📝 Social Posts & Interactions
- Create and manage posts.
- Share images and media.
- Public and friends-only content visibility.
- Like and unlike posts.
- Comment on posts.
- Paginated content feeds.

### 🔔 Notification System
- Notification management.
- Notification history.
- Read and unread notification states.
- Notification preferences.
- Event-driven notification generation.
- WebSocket-based notification delivery where configured.

### 💬 Real-Time Communication
- Private conversations.
- Persistent message storage.
- WebSocket-based communication architecture.
- STOMP messaging configuration.
- Authenticated WebSocket connections.

### 🔐 Privacy & Security
- JWT-based authentication.
- Protected REST endpoints.
- Resource-level access control.
- Public and friends-only content restrictions.
- Blocking and interaction restrictions.
- Input validation and secure media handling.

---

## 🛠️ Technology Stack

| Category | Technologies |
|---|---|
| Programming Language | Java 21 |
| Backend Framework | Spring Boot 3.x |
| Security | Spring Security, JWT |
| Database | PostgreSQL |
| ORM | Hibernate, Spring Data JPA |
| Build Tool | Maven |
| API Development | Spring Web, REST APIs |
| Validation | Jakarta Bean Validation |
| Real-Time Communication | WebSocket, STOMP |
| Testing | JUnit 5, Mockito |
| API Testing | Postman |
| Version Control | Git, GitHub |

**Planned frontend:** React, TypeScript and Tailwind CSS.

---

## 🏗️ System Architecture

VibeCircle follows a **layered, modular monolithic architecture** to maintain separation of concerns, maintainability and scalability.

```text
              CLIENT APPLICATION
                      |
                      v
             SPRING BOOT REST API
                      |
                      v
              SPRING SECURITY
                  + JWT
                      |
                      v
              CONTROLLER LAYER
                      |
                      v
               SERVICE LAYER
                      |
       +--------------+--------------+
       |              |              |
       v              v              v
    AUTH           DISCOVERY       FRIENDSHIP
       |              |              |
       v              v              v
    PROFILE          POSTS       NOTIFICATIONS
       |              |              |
       +--------------+--------------+
                      |
                      v
              REPOSITORY LAYER
                      |
                      v
                 POSTGRESQL

        Supporting Infrastructure
        --------------------------
        WebSocket + STOMP
        Media Storage
        Spring Security
```

### Backend Architecture Principles

- Separation of controller, service and repository responsibilities.
- DTO-based request and response handling.
- Entity relationships managed through JPA and Hibernate.
- Centralized authentication and authorization.
- Database-driven persistence.
- Modular organization of business functionality.

---

## 🧠 Interest-Based Recommendation Algorithm

One of the central features of VibeCircle is its city-based interest discovery engine.

The recommendation process works in four stages:

**1. City Filtering**

Retrieve eligible users belonging to the same city.

**2. Interest Matching**

Identify common hobbies and interests between users.

**3. Similarity Calculation**

VibeCircle uses the Jaccard similarity coefficient:

\[
J(A,B)=\frac{|A\cap B|}{|A\cup B|}\times100
\]

Where:

- A represents the interests of the current user.
- B represents the interests of a candidate user.
- Intersection represents shared interests.
- Union represents all unique interests.

**4. Recommendation Ranking**

Rank eligible users based on shared interests and other supported recommendation criteria.

This helps users discover people with common interests rather than relying only on existing social connections.

---

## 🗄️ Database Design

VibeCircle uses PostgreSQL as its primary relational database.

The database contains dedicated tables for different application modules.

| Module | Database Tables |
|---|---|
| User Management | users, user_profiles |
| Interests | interests, user_interests |
| Location | cities |
| Friendship | friend_requests, friendships, blocks |
| Discovery | hidden_recommendations |
| Posts | posts, post_media, post_likes |
| Comments | comments |
| Hashtags | hashtags, post_hashtags |
| Stories | stories, story_views |
| Messaging | conversations, conversation_members, messages |
| Notifications | notifications, notification_preferences |
| Security | refresh_tokens, verification_tokens |
| Moderation | reports |
| Communication | calls |

The database uses relational constraints and entity relationships to maintain data consistency.

---

## 📁 Project Structure

```text
vibecircle/
│
├── src/
│   ├── main/
│   │   ├── java/com/vibecircle/
│   │   │
│   │   ├── auth/
│   │   ├── user/
│   │   ├── profile/
│   │   ├── interest/
│   │   ├── discovery/
│   │   ├── friendship/
│   │   ├── post/
│   │   ├── comment/
│   │   ├── media/
│   │   ├── notification/
│   │   ├── chat/
│   │   ├── story/
│   │   ├── privacy/
│   │   ├── security/
│   │   └── config/
│   │
│   └── resources/
│       └── application.properties
│
├── src/test/
│
├── uploads/
├── pom.xml
├── .gitignore
└── README.md
```

Each functional module follows appropriate layered responsibilities using controllers, services, repositories, entities and DTOs.

---

## ⚙️ Getting Started

Follow these steps to run the backend locally.

### Prerequisites

Install the following:

- Java 21
- Maven
- PostgreSQL
- Git
- IntelliJ IDEA or VS Code
- Postman (recommended)

### 1. Clone the Repository

```bash
git clone https://github.com/RitikaSinha19/VibeCircle.git
```

### 2. Navigate to the Project

```bash
cd VibeCircle
```

### 3. Create the PostgreSQL Database

```sql
CREATE DATABASE vibecircle;
```

Configure your database credentials using environment variables or a local configuration file.

### 4. Configure Environment Variables

Example configuration:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/vibecircle}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
```

Configure the remaining application-specific settings, including JWT secrets, media storage and server configuration, according to your local environment.

**Never commit database passwords, JWT secrets or API credentials to GitHub.**

### 5. Build the Application

```bash
mvn clean install
```

### 6. Run the Application

```bash
mvn spring-boot:run
```

The backend will run on:

```text
http://localhost:8080
```

---

## 🧪 Testing

API testing can be performed using Postman.

The project should be tested across the following scenarios:

- User registration and login.
- Authentication and protected endpoints.
- Profile and interest management.
- User discovery and recommendation.
- Friend request lifecycle.
- Post creation and interactions.
- Content visibility and privacy restrictions.
- Notification generation and retrieval.
- WebSocket authentication and messaging.

Unit and integration testing should cover business logic, repository operations, security and application workflows.

---

## 🔮 Future Enhancements

The following capabilities are planned for future development:

- React-based responsive frontend.
- Advanced real-time messaging.
- Story expiration and media management.
- Voice and video communication using WebRTC.
- Redis-based caching and presence management.
- Advanced recommendation ranking.
- Docker-based deployment.
- Cloud media storage.
- Production deployment and monitoring.

---

## 🎯 Project Objectives

VibeCircle aims to:

1. Make discovering like-minded individuals easier.
2. Enable city-level social discovery while protecting exact location privacy.
3. Build meaningful social connections through shared interests.
4. Provide flexible content-sharing and privacy controls.
5. Support secure social interactions and real-time communication.

---

## 👩‍💻 Author

**Ritika Sinha**

B.Tech | Computer Science & Engineering

GitHub: https://github.com/RitikaSinha19

---

## 💡 Project Philosophy

> Great connections begin with shared interests.

VibeCircle is built around the idea that meaningful social connections can start with something as simple as a shared hobby, passion or curiosity.

**Find your people. Share your interests. Build your circle.** 🌐

---

*Built with Java, Spring Boot and PostgreSQL.*

