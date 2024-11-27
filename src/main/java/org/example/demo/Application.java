package org.example.demo;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.Pair;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

import static org.example.demo.Game.SetupBoard;

public class Application extends javafx.application.Application {

    private String userName;

    private Socket socket;

    private BufferedReader reader;

    private BufferedWriter writer;

    private Controller controller;

    private VBox root;

    private Label statusLabel;

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void start(Stage stage) throws IOException {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter your name:");

        Optional<String> result = dialog.showAndWait();
        String userName = result.orElse("null");

        // let user choose board size
        int[] size = getBoardSizeFromUser();

        FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("board.fxml"));
        root = fxmlLoader.load();
        controller = fxmlLoader.getController();

        setUserName(userName);

        String message = connectToServer("localhost", 1234, size[0], size[1]);

        Controller.game = new Game(SetupBoard(size[0], size[1]));

        controller.createGameBoard();

        // 设置窗口关闭事件，点击关闭按钮时退出程序
        stage.setOnCloseRequest(event -> {
            System.exit(0);
        });

        // Display appropriate dialog based on the message received
        if ("wait".equalsIgnoreCase(message)) {

            statusLabel = new Label();

            statusLabel.setText("Waiting for other player...");
            root.getChildren().add(statusLabel); // root是应用程序的主布局容器
            // Proceed with waiting logic
            new Thread(() -> {
                if(waiting()){
                    Platform.runLater(() -> {
                        // Update the text to "Match"
                        statusLabel.setText("Match");

                        // Create a timeline to remove the label after 5 seconds
                        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
                            root.getChildren().remove(statusLabel);
                        }));
                        timeline.setCycleCount(1);
                        timeline.play();
                    });
                }
            }).start();
        }
        if ("start".equalsIgnoreCase(message)) {
            // Show matching successful dialog
            Alert startAlert = new Alert(Alert.AlertType.INFORMATION);
            startAlert.setTitle("Match Found");
            startAlert.setHeaderText("Match Successful!");
            startAlert.setContentText("A player has connected. The game is starting.");
            startAlert.showAndWait();
        }

        Scene scene = new Scene(root);
        stage.setTitle("Linking Game");
        stage.setScene(scene);
        stage.show();
        // TODO: handle the game logic

        // receive the board from the server
        // update the board and in the controller

        Controller.writer = writer;

        new Thread(this::listenForMessages).start();

    }


    public String connectToServer(String serverAddress, int port, int row, int col) {
        try {
            // Connect to the server and set up input/output streams
            socket = new Socket(serverAddress, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Send the username to the server
            writer.write(userName + "\n");
            writer.flush();

            // Send the board size to the server
            writer.write(row + "\n");
            writer.flush();

            writer.write(col + "\n");
            writer.flush();

            // wait for the game to start
            String message = reader.readLine();
            System.out.println("Message received: " + message);

            // Start a new thread to listen for messages from the server
//            new Thread(this::listenForMessages).start();
            return message;
        } catch (IOException e) {
            System.out.println("Unable to connect to server: " + e.getMessage());
        }
        return "Error connecting to server.";
    }

    public Boolean waiting(){
        String message;
        System.out.println("Waiting Start");
        try {
            while ((message = reader.readLine()) != null) {
                if("Start".equalsIgnoreCase(message)){
                    sendMessage("WER");
                    return true;
                }
            }
            System.out.println("Stopped listening for Waiting Start...");
        } catch (IOException e) {
            System.out.println("Error reading message: " + e.getMessage());
        }
        return false;
    }




    private synchronized void sendMessage(String message) {
        try {
            if (writer != null) {
                writer.write( message + "\n");
                writer.flush();
                System.out.println("Message sent: " + message);
            }
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }


    private void listenForMessages() {
        String message = "";
        int status = 0;
        System.out.println("Started listening for game status...");
        try {
            System.out.println("Listening for game status...");
            while ((message = reader.readLine()) != null) {
                // 这里处理从服务器接收到的其他消息
                System.out.println("----------------------------------------------------");
                System.out.println("Message received: " + message);

                if (message.equals("Status")) {
                    // Read the board state
                    int[][] board = new int[Controller.game.row][Controller.game.col];
                    String boardString = reader.readLine();
                    System.out.println("Board string: " + boardString);
                    String[] boardData = boardString.trim().split(" ");
                    int index = 0;
                    for (int i = 0; i < Controller.game.row; i++) {
                        for (int j = 0; j < Controller.game.col; j++) {
                            board[i][j] = Integer.parseInt(boardData[index++]);
                        }
                    }
                    Controller.game.board = board;

                    // Read player's score
                    int playerScore = Integer.parseInt(reader.readLine().trim());

                    // Read opponent's score
                    int opponentScore = Integer.parseInt(reader.readLine().trim());
//                    opponent.score = opponentScore;

                    Platform.runLater(() -> {
                        controller.updateScoreLabel(playerScore);
                        controller.createGameBoard();
                    });
                }if (message.equals("Your Turn")) {
                    Controller.myTurn = true;
                    Platform.runLater(() -> {
                        controller.updateTurnLabel();
                    });
                } else if (message.equals("Opponent Turn")) {
                    Controller.myTurn = false;
                    Platform.runLater(() -> {
                        controller.updateTurnLabel();
                    });
                } else if (message.equals("Win")) {
                    Platform.runLater(() -> {
                        controller.updateResultLabel("You Win!");
                    });
                    break;
                } else if (message.equals("Lose")) {
                    Platform.runLater(() -> {
                        controller.updateResultLabel("You Lose!");
                    });
                    break;
                } else if (message.equals("Draw")) {
                    Platform.runLater(() -> {
                        controller.updateResultLabel("Draw!");
                    });
                    break;
                } else if (message.equals("Opponent Loss")) {
                    Platform.runLater(() -> {
                        controller.updateResultLabel("Opponent Loss the Connection, YOU WIN!");
                    });
                    break;
                }
            }
            System.out.println("Stopped listening game status...");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error reading message: " + e.getMessage());
        } finally {
//            assert message != null;
            System.out.println("Message(finally): " + message);
            if(message == null) {
                closeConnections();
                Platform.runLater(() -> {
                    controller.updateResultLabel("Server Error");
                });
            }else  if(message.equals("Opponent Loss")|| message.equals("Draw") || message.equals("Win") || message.equals("Lose")){
                closeConnections();
            }else {
                sendMessage("Loss");
//                closeConnections();
            }

        }
    }


    public void closeConnections() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Error closing connections: " + e.getMessage());
        }
    }

    // let user choose board size
    private int[] getBoardSizeFromUser() {

        // TODO: let user choose board size
        while (true) {
            Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
            dialog.setTitle("Board Size");
            dialog.setHeaderText("Choose the board size (rows and columns)");

            // Set up the labels and text fields
            Label rowLabel = new Label("Rows:");
            TextField rowInput = new TextField();
            rowInput.setPromptText("Enter number of rows");

            Label colLabel = new Label("Columns:");
            TextField colInput = new TextField();
            colInput.setPromptText("Enter number of columns");

            // Layout the dialog
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.add(rowLabel, 0, 0);
            grid.add(rowInput, 1, 0);
            grid.add(colLabel, 0, 1);
            grid.add(colInput, 1, 1);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    try {
                        int rows = Integer.parseInt(rowInput.getText());
                        int cols = Integer.parseInt(colInput.getText());
                        return new Pair<>(rows, cols);
                    } catch (NumberFormatException e) {
                        // Invalid input, return null to indicate failure
                        return null;
                    }
                }
                return null;
            });

            Optional<Pair<Integer, Integer>> result = dialog.showAndWait();
            if (result.isPresent()) {
                int rows = result.get().getKey();
                int cols = result.get().getValue();
                if ((rows * cols) % 2 == 0) {
                    return new int[]{rows, cols};  // Return valid board size
                } else {
                    // Display error dialog if product is not even
                    Dialog<Void> errorDialog = new Dialog<>();
                    errorDialog.setTitle("Invalid Board Size");
                    errorDialog.setHeaderText("The product of rows and columns must be even. Please try again.");
                    errorDialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    errorDialog.showAndWait();
                }
            } else {
                // If dialog is cancelled, return default size
                return new int[]{4, 4};
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}