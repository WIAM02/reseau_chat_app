import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    private Map<String, ObjectOutputStream> clients = new HashMap<>();
    private Connection databaseConnection;


    public ChatServer(int port) {


        try {
            databaseConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/APP", "root", "");
            System.out.println("Database connection established.");

            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream inputStream;
        private ObjectOutputStream outputStream;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                outputStream = new ObjectOutputStream(socket.getOutputStream());
                inputStream = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String message;
                    try {
                        message = (String) inputStream.readObject();
                    } catch (EOFException e) {
                        System.out.println("Client disconnected.");
                        break;
                    }
                    if (message.startsWith("JOIN")) {
                        handleJoin(message);
                    } else if (message.startsWith("PRIVATE")) {
                        handlePrivateMessage(message);
                    } else if (message.startsWith("FILE")) {
                        // Handle file transfer
                        String[] parts = message.split(" ", 3);
                        String recipient = parts[1];
                        String content = parts[2];
                        receiveFile(recipient, content);

                    } else if (message.startsWith("ADD_CONTACT")) {
                        // Handle request to add contact
                        String name = (String) inputStream.readObject();
                        String email = (String) inputStream.readObject();
                        insertContact(email, name, username);
                    }

                }
            } catch (IOException | ClassNotFoundException | GeneralSecurityException e) {
                e.printStackTrace();
            } finally {
                if (username != null) {
                    clients.remove(username);
                    sendUserList();
                    sendContactList();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        private void receiveFile(String recipient, String fileName) {
            try {
                // Read the recipient information
                String recipientInfo = (String) inputStream.readObject();

                // Read the file content
                byte[] fileContent = (byte[]) inputStream.readObject();

                // Handle the received file content
                // For example, you can save it to a file
                FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                fileOutputStream.write(fileContent);
                fileOutputStream.close();

                // Notify the recipient about the received file
                ObjectOutputStream recipientOutput = clients.get(recipient);
                if (recipientOutput != null) {
                    recipientOutput.writeObject("FILE_RECEIVED " + username + " " + fileName);
                    recipientOutput.flush();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        //login
        private void handleJoin(String message) throws IOException, GeneralSecurityException {

            String[] parts = message.split(" ", 3);
            String username = parts[1];
            String email = parts[2];
            if (validateCredentials(username, email)) {
                this.username = username;
                outputStream.writeObject("LOGIN_SUCCESSFUL");
                clients.put(username, outputStream);
                sendUserList();
                sendContactList();

                // Send conversation history for each contact
                sendConversationHistoryForContacts(username);
            } else {
                outputStream.writeObject("INVALID_CREDENTIALS");
                outputStream.flush();
            }
        }

        private boolean validateCredentials(String username, String email) {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement("SELECT * FROM login WHERE username=? AND email=?");
                statement.setString(1, username);
                statement.setString(2, email);
                ResultSet resultSet = statement.executeQuery();
                return resultSet.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void handlePrivateMessage(String message) throws IOException, GeneralSecurityException {
            // Splitting into at most 6 parts as expected
            String[] parts = message.split(" ", 6);


            if (parts.length < 6) {
                System.out.println("Invalid message format: " + message);
                return;
            }

            String sender = parts[1];
            String recipient = parts[2];
            String encryptedAESKey = parts[3];
            String encryptedMessage = parts[4];
            String keypublick = parts[5];

            System.out.println("Received message from " + sender + " to " + recipient);
            System.out.println("encryptedAESKey " + encryptedAESKey);

            sendPrivateMessage(sender, recipient, encryptedAESKey, encryptedMessage, keypublick);

            if (!clients.containsKey(recipient)) {
                int lastColonIndex = message.lastIndexOf(":");
                String lastWord = message.substring(lastColonIndex + 1);
                insertNotification(sender, recipient, lastWord);
            }
        }

        private void sendUserList() {
            StringBuilder userListMessage = new StringBuilder("USERLIST");
            for (String user : clients.keySet()) {
                userListMessage.append(" ").append(user);
            }
            for (ObjectOutputStream clientOutput : clients.values()) {
                try {
                    clientOutput.writeObject(userListMessage.toString());
                    clientOutput.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendPrivateMessage(String sender, String recipient, String encryptedAESKey, String encryptedMessage, String PublicKey) throws IOException {
            ObjectOutputStream recipientOutput = clients.get(recipient);
            if (recipientOutput != null) {
                String info = "PRIVATE " + sender + " " + encryptedAESKey + " " + encryptedMessage + " " + PublicKey;
                recipientOutput.writeObject(info);
                recipientOutput.flush();
            }
        }


        // Display response

        private void sendContactList() {
            for (String user : clients.keySet()) {
                try {
                    sendContactList(user);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendContactList(String username) throws IOException {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement("SELECT username_contact FROM contacts WHERE username_user=?");
                statement.setString(1, username);
                ResultSet resultSet = statement.executeQuery();

                StringBuilder contactListMessage = new StringBuilder("CONTACTLIST");
                while (resultSet.next()) {
                    String contact = resultSet.getString("username_contact");
                    boolean isActive = clients.containsKey(contact);
                    String status = isActive ? "Active" : "Inactive";
                    contactListMessage.append(" ").append(contact).append(" (").append(status).append(")");
                }

                ObjectOutputStream output = clients.get(username);
                if (output != null) {
                    output.writeObject(contactListMessage.toString());
                    output.flush();
                }

                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        //si user inactive

        private void insertNotification(String username, String recipient, String message) {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO notification(username, recepient, message) VALUES (?, ?, ?)");
                statement.setString(1, username);
                statement.setString(2, recipient);
                statement.setString(3, message);
                int rowsInserted = statement.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("Notification inserted successfully.");
                } else {
                    System.out.println("Failed to insert notification.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void insertContact(String email, String contact, String username) {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO contacts(email_cn, username_contact,  username_user) VALUES (?, ?, ?)");
                statement.setString(1, email);

                statement.setString(2, contact);

                statement.setString(3, username);
                int rowsInserted = statement.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("contact inserted successfully.");
                    sendContactList(username);

                } else {
                    System.out.println("Failed to insert contact.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void sendConversationHistory(String sender, String recipient, ObjectOutputStream outputStream) {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement(
                        "SELECT * FROM notification WHERE (username = ? AND recepient = ?) "
                );

                statement.setString(1, recipient);
                statement.setString(2, sender);

                ResultSet resultSet = statement.executeQuery();

                StringBuilder conversationHistory = new StringBuilder();
                while (resultSet.next()) {
                    System.out.println("resultSet : " + resultSet);
                    String msgSender = resultSet.getString("username");
                    System.out.println("msgSender : " + msgSender);
                    String message = resultSet.getString("message");
                    System.out.println("message : " + message);
                    System.out.println("message decrypted : " + message);
                    conversationHistory.append(msgSender).append(": ").append(message).append("\n");
                }

                // Send conversation history back to the client
                outputStream.writeObject("CONVERSATION_HISTORY " + recipient + " " + conversationHistory.toString());
                outputStream.flush();

                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void sendConversationHistoryForContacts(String username) {
            try {
                PreparedStatement statement = databaseConnection.prepareStatement("SELECT username_contact FROM contacts WHERE username_user=?");
                statement.setString(1, username);
                ResultSet resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String contact = resultSet.getString("username_contact");
                    sendConversationHistory(username, contact, clients.get(username));
                }

                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        new ChatServer(12345);
    }
}