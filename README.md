# Collaborative Text Editor

This is a real-time collaborative text editor application built with Java, Spring Boot, JavaFX, and WebSocket. It supports multiple users editing a shared document simultaneously, with features like:

- Undo/redo functionality
- Commenting on text
- Real-time user cursors
- Role-based access (editor/viewer)

## Project Structure

- **`server/`**: Spring Boot backend handling WebSocket communication, CRDT document management, and REST APIs.
- **`client/`**: JavaFX frontend for the collaborative editor interface.
- **`src/main/resources/`**: Contains FXML files (`welcome.fxml`, `editor.fxml`) and CSS (`styles.css`).

## Setup

### Prerequisites

- **Java 17 or higher**: Ensure the Java Development Kit (JDK) is installed.
- **Maven**: For building and managing dependencies.
- **Git**: For cloning the repository.
- **Network**: Server and clients must be on the same network for automatic server discovery.

### Clone the Repository

```bash
git clone https://github.com/3atar2004/Collaborative_Text_Editor.git
cd collaborative-text-editor
```

### Install Dependencies

1. Navigate to the project root directory.
2. Run the following command to download dependencies:

```bash
mvn clean install
```

## Running the Application

### Start the Server

Run the server to handle WebSocket connections and document management:

```bash
mvn spring-boot:run -pl server
```

The server will start on `http://localhost:8080` and listen for WebSocket connections at `ws://localhost:8080/ws`.

### Start the Client

Launch the JavaFX client to access the editor interface:

```bash
mvn javafx:run -pl client
```

### Using an IDE (e.g., IntelliJ IDEA)

1. Open the project in IntelliJ using the repository URL: `https://github.com/3atar2004/Collaborative_Text_Editor.git`.
2. Run the server application first (`ServerApplication.java`).
3. Run as many client instances as needed (`CollaborativeEditorApp.java`).

## Notes

- Ensure the server is running before starting any client instances.
- If the client cannot find the server, manually enter the server's IP address when prompted.
- The application uses a CRDT (Conflict-free Replicated Data Type) for conflict-free editing.
- For large-scale use, consider optimizing memory usage and adding persistence (see project documentation for details).

## License

This project is licensed under the MIT License.

---

For issues or contributions, visit the [GitHub repository](https://github.com/3atar2004/Collaborative_Text_Editor).
