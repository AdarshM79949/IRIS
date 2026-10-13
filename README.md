# IRIS — Real-time Messaging & Collaborative Creative Canvas ◈

**IRIS** is a modern, real-time messaging, group collaboration, and creative canvas platform powered by **Java (Spring Boot 3)**, WebSockets (ASP.NET SignalR protocol compatibility), and an AI drawing critique module.

---

## 🌟 Key Features
- ✅ **User Authentication & Identity** – Fast registration (optional avatar with auto UI-Avatars fallback), BCrypt password hashing, and JWT token authentication.
- ✅ **SignalR Real-Time Protocol Compatibility** – Full implementation of the SignalR WebSockets protocol (`/hubs/chat` and `/hubs/chat/negotiate`) with JSON hub framing (`\u001e`).
- ✅ **Live Instant Messaging** – Sending & receiving messages in real time, message history with pagination, unread message counts, and read status markers.
- ✅ **Typing Indicators** – Real-time broadcast of typing status between active chat participants (`NotifyTyping` & `NotifyTypingToUser`).
- ✅ **Real-Time Online/Offline Status** – Live presence tracking (`OnlineUsers`) and push notifications when friends come online (`Notify`).
- ✅ **Friend Request System** – Send requests, accept requests, list incoming requests (`/firendsrequestsforme`), and list sent requests (`/firendsrequestssendbyme`).
- ✅ **Group Creation & Multi-User Group Chat**:
  - **Create Groups**: Choose a group name and select added friends with checkboxes to create custom group rooms.
  - **Group Messaging**: Real-time group messaging where each participant's messages are tagged with their author name and a distinct member color.
  - **Member Management**: List user groups, member counts, and add new members to groups dynamically via REST & WebSocket.
- ✅ **Live Collaborative Drawing Canvas (1-on-1 & Multi-User Groups)**:
  - Real-time co-drawing module directly inside the chat section (referencing [LiveKit LivePaint](https://github.com/livekit-examples/livepaint.git)):
  - **Tools**: Freehand Brush, Eraser, Straight Line, Box / Rectangle, Circle with live shape drag preview.
  - **Colors & Stroke**: Color palette presets + custom hex color picker + stroke size slider (1px-30px).
  - **History**: Full Undo/Redo stack with snapshot retention.
  - **Normalized Coordinates**: Device-independent coordinate normalization ($x \in [0, 1], y \in [0, 1]$) for cross-device accuracy.
  - **Live Multi-User Cursors**: Floating cursor badges tracking each member's real-time position with custom colors and names.
  - **Group Live Drawing Sync**: Real-time broadcast of group strokes, shape updates, and canvas clearing across all active group members.
  - **Send Drawing to Chat**: Capture the shared canvas and share it directly into the 1-on-1 or group chat conversation.
- ✅ **AI Drawing Critique & Improvement**:
  - Integrated AI assistant analyzing drawings across 5 dimensions (Composition, Technique, Architecture, Shading, Comprehensive).
  - One-click share advice directly to chat conversation.
- ✅ **Cloud-Ready Split Deployment**:
  - **Frontend**: Lightweight static app ready for **Vercel** (`frontend/vercel.json`).
  - **Backend**: Containerized with **Docker** & **Render** (`render.yaml`).
  - **Embedded**: Also served directly at `http://localhost:5000/` by the Spring Boot JAR for local development.

---

## 🛠️ Architecture & Conversion Mapping

| ASP.NET Core Component | Java / Spring Boot Equivalent |
|---|---|
| `Program.cs` & Endpoints (`MapGroup("/api/account")`) | [`AccountController.java`](file:///e:/IRIS/src/main/java/com/chatapp/controller/AccountController.java) |
| `ChatHub.cs` (SignalR Hub) | [`SignalRChatWebSocketHandler.java`](file:///e:/IRIS/src/main/java/com/chatapp/websocket/SignalRChatWebSocketHandler.java) + [`WebSocketConfig.java`](file:///e:/IRIS/src/main/java/com/chatapp/config/WebSocketConfig.java) |
| `/hubs/chat/negotiate` | [`SignalRNegotiateController.java`](file:///e:/IRIS/src/main/java/com/chatapp/controller/SignalRNegotiateController.java) |
| `TokenService.cs` & `JwtBearerEvents` | [`JwtUtil.java`](file:///e:/IRIS/src/main/java/com/chatapp/config/JwtUtil.java) + [`JwtAuthenticationFilter.java`](file:///e:/IRIS/src/main/java/com/chatapp/config/JwtAuthenticationFilter.java) |
| `FileUpload.cs` & Static files | [`FileStorageService.java`](file:///e:/IRIS/src/main/java/com/chatapp/service/FileStorageService.java) + [`WebMvcConfig.java`](file:///e:/IRIS/src/main/java/com/chatapp/config/WebMvcConfig.java) |
| `friendsRequestsData.cs` | [`FriendRequestService.java`](file:///e:/IRIS/src/main/java/com/chatapp/service/FriendRequestService.java) + [`FriendsRequestRepository.java`](file:///e:/IRIS/src/main/java/com/chatapp/repository/FriendsRequestRepository.java) |
| `AppDbContext.cs` & `chat.db` | Spring Data JPA with SQLite JDBC & Hibernate 6 Community Dialect |
| `AppUser.cs`, `Message.cs`, `FriendsRequests.cs` | [`AppUser.java`](file:///e:/IRIS/src/main/java/com/chatapp/model/AppUser.java), [`Message.java`](file:///e:/IRIS/src/main/java/com/chatapp/model/Message.java), [`FriendsRequest.java`](file:///e:/IRIS/src/main/java/com/chatapp/model/FriendsRequest.java) |

---

## 🗄️ Hot / Cold Storage Configuration

### 1. Hot Storage: Supabase (PostgreSQL)
Supabase acts as the primary relational database for users, friendships, and message history.

Set your Supabase credentials in [`application.properties`](file:///e:/IRIS/src/main/resources/application.properties) or via environment variables:
```bash
# Environment variables:
export SUPABASE_DB_URL="jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres?sslmode=require"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="<your-supabase-db-password>"
```
Or start with the pre-configured `supabase` profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=supabase
# or with JAR:
java -jar target/chat-app-backend-1.0.0.jar --spring.profiles.active=supabase
```
*Note: If no Supabase URL is specified, the application automatically uses local SQLite (`chat.db`) for effortless zero-config development.*

### 2. Cold Storage: Discord (Media & Attachments)
Discord acts as the cold file and media archive. Uploaded avatars and chat files are posted to a Discord channel via Webhook. Discord hosts the files permanently on its high-speed CDN (`cdn.discordapp.com`).

Set your Discord channel Webhook URL in [`application.properties`](file:///e:/IRIS/src/main/resources/application.properties) or via environment variable:
```bash
export DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/<WEBHOOK_ID>/<WEBHOOK_TOKEN>"
```

- When a user signs up with a profile picture or uploads a media file to `/api/account/upload`, the file is archived to Discord.
- The returned Discord CDN URL (`https://cdn.discordapp.com/attachments/...`) is stored in the database.
- If `DISCORD_WEBHOOK_URL` is empty or Discord is unreachable, the system gracefully falls back to local disk storage (`/uploads/`).

---

## 🚀 How to Run

### 1. Requirements
- **Java**: JDK 17 or higher (JDK 17 and 21 supported)
- **Maven**: 3.8+ (wrapper or system Maven)

### 2. Run the Java Backend
From the workspace root directory (`e:\IRIS`):
```bash
mvn spring-boot:run
```
Or run the packaged JAR directly:
```bash
java -jar target/chat-app-backend-1.0.0.jar
```

The server starts up on port **`5000`** (`http://localhost:5000`).

### 3. Accessing the Application

#### Option A: Built-in Web Client
Simply navigate to:
```
http://localhost:5000
```
You can register multiple user accounts (or open two browser windows / private tabs) to test real-time chat, notifications, friend requests, and typing indicators.

#### Option B: Angular Frontend Client
The original Angular application in `original-chat-app/Client` is pre-configured to connect to port 5000.
```bash
cd original-chat-app/Client
npm install
npm start
```
Then visit `http://localhost:4200`. It will connect seamlessly to your Spring Boot backend!

### 4. Running the Tests
```bash
mvn test
```
All integration tests (Auth, JWT, Friend Requests, Chat messages) execute against an SQLite test instance and pass automatically.
