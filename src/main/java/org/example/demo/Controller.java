package org.example.demo;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import javafx.scene.layout.VBox;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

public class Controller {

    @FXML
    private Label scoreLabel;

    @FXML
    private Label turnLabel;

    @FXML
    private Label resultLabel;

    @FXML
    private GridPane gameBoard;

    @FXML
    private Pane linePane;

    public static Game game;

    public static BufferedWriter writer;

    public static boolean myTurn = false;



    int[] position = new int[3];
    private int score = 0;


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

    @FXML
    public void initialize() {
        linePane.setMouseTransparent(true);
    }

    public void createGameBoard() {

        gameBoard.getChildren().clear();


        for (int row = 0; row < game.row; row++) {
            for (int col = 0; col < game.col; col++) {
                Button button = new Button();
                button.setPrefSize(40, 40);
                ImageView imageView = addContent(game.board[row][col]);
                imageView.setFitWidth(30);
                imageView.setFitHeight(30);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                int finalRow = row;
                int finalCol = col;
                button.setOnAction( _ -> handleButtonPress(finalRow, finalCol));
                gameBoard.add(button, col, row);
            }
        }

    }
    // 判断并绘制连接线的主方法
    public void drawConnection(int row1, int col1, int row2, int col2) {
        if (game.isDirectlyConnected(row1, col1, row2, col2, game.board)) {
            drawLine(row1, col1, row2, col2);  // 一线连接
            return;
        }
        if ((row1 != row2) && (col1 != col2)) {
            if (game.board[row1][col2] == 0 && game.isDirectlyConnected(row1, col1, row1, col2, game.board)
                    && game.isDirectlyConnected(row1, col2, row2, col2, game.board)) {
                drawLine(row1, col1, row1, col2);  // 两线连接的第一条线
                drawLine(row1, col2, row2, col2);  // 两线连接的第二条线
                return;
            } else if (game.board[row2][col1] == 0 && game.isDirectlyConnected(row2, col2, row2, col1, game.board)
                    && game.isDirectlyConnected(row2, col1, row1, col1, game.board)) {
                drawLine(row2, col2, row2, col1);
                drawLine(row2, col1, row1, col1);
                return;
            }
        }
        if (row1 != row2) {
            for (int i = 0; i < game.board[0].length; i++) {
                if (game.board[row1][i] == 0 && game.board[row2][i] == 0 &&
                        game.isDirectlyConnected(row1, col1, row1, i, game.board) &&
                        game.isDirectlyConnected(row1, i, row2, i, game.board) &&
                        game.isDirectlyConnected(row2, col2, row2, i, game.board)) {
                    drawLine(row1, col1, row1, i);
                    drawLine(row1, i, row2, i);
                    drawLine(row2, i, row2, col2);
                    return;
                }
            }
        }
        if (col1 != col2) {
            for (int j = 0; j < game.board.length; j++) {
                if (game.board[j][col1] == 0 && game.board[j][col2] == 0 &&
                        game.isDirectlyConnected(row1, col1, j, col1, game.board) &&
                        game.isDirectlyConnected(j, col1, j, col2, game.board) &&
                        game.isDirectlyConnected(row2, col2, j, col2, game.board)) {
                    drawLine(row1, col1, j, col1);
                    drawLine(j, col1, j, col2);
                    drawLine(j, col2, row2, col2);
                    return;
                }
            }
        }

    }

    private void drawLine(int row1, int col1, int row2, int col2) {
        // 获取按钮的实际位置
        Button startButton = (Button) getNodeByRowColumnIndex(row1, col1, gameBoard);
        Button endButton = (Button) getNodeByRowColumnIndex(row2, col2, gameBoard);

        if (startButton == null || endButton == null) {
            return; // 如果按钮不存在，则退出
        }

        // 获取按钮的中心点在场景中的坐标
        double startX = startButton.localToScene(startButton.getWidth() / 2, startButton.getHeight() / 2).getX();
        double startY = startButton.localToScene(startButton.getWidth() / 2, startButton.getHeight() / 2).getY();
        double endX = endButton.localToScene(endButton.getWidth() / 2, endButton.getHeight() / 2).getX();
        double endY = endButton.localToScene(endButton.getWidth() / 2, endButton.getHeight() / 2).getY();

        // 转换为 linePane 的坐标系
        double paneStartX = linePane.sceneToLocal(startX, startY).getX();
        double paneStartY = linePane.sceneToLocal(startX, startY).getY();
        double paneEndX = linePane.sceneToLocal(endX, endY).getX();
        double paneEndY = linePane.sceneToLocal(endX, endY).getY();

        Line line = new Line(paneStartX, paneStartY, paneEndX, paneEndY);
        line.setStrokeWidth(3);
        line.setStroke(javafx.scene.paint.Color.RED);
        linePane.getChildren().add(line);

        PauseTransition pause = new PauseTransition(Duration.seconds(0.3));
        pause.setOnFinished(event -> linePane.getChildren().remove(line));
        pause.play();
    }

    // 辅助方法：获取 GridPane 中指定位置的节点
    private Node getNodeByRowColumnIndex(final int row, final int column, GridPane gridPane) {
        for (Node node : gridPane.getChildren()) {
            if (GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) == row &&
                    GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == column) {
                return node;
            }
        }
        return null;
    }



    private void handleButtonPress(int row, int col) {
        System.out.println("Button pressed at: " + row + ", " + col);
        if(position[0] == 0){
            position[1] = row;
            position[2] = col;
            position[0] = 1;
        }else{
            if(writer == null){
                // 单人游戏
                boolean change = game.judge(position[1], position[2], row, col);
                // position[0] = 0;
                if(change){
                    // TODO: handle the grid deletion logic
                    drawConnection(position[1], position[2], row, col);

                    position[0] = 0;
                    System.out.println("Matched!");
                    game.board[position[1]][position[2]] = 0;
                    game.board[row][col] = 0;


                    // update the game board
                    createGameBoard();

                    // update the score
                    score += 10;
                    updateScoreLabel(this.score);

                    // check if the game is over
                    checkGameOver();

                }else {
                    position[1] = row;
                    position[2] = col;
                }
            }else {
                // 多人游戏
                boolean change = game.judge(position[1], position[2], row, col);
                if(myTurn){
                    if(change){

                        System.out.println(writer);
                        sendMessage("Valid");

                        drawConnection(position[1], position[2], row, col);
                        position[0] = 0;
                        System.out.println("Matched!");
                        game.board[position[1]][position[2]] = 0;
                        game.board[row][col] = 0;

                        StringBuilder newBoard = new StringBuilder();
                        for (int i = 0; i < game.row; i++) {
                            for (int j = 0; j < game.col; j++) {
                                newBoard.append(game.board[i][j]).append(" ");
                            }
                        }
                        sendMessage(newBoard.toString());
                    }else {
                        sendMessage("Invalid");
                        resultLabel.setText("Invalid Move");
                        // 设置成红色
                        resultLabel.setStyle("-fx-text-fill: red;");
                        // 创建一个 Timeline 在 5 秒后清除标签文本
                        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                            resultLabel.setText("Playing"); // 清空标签内容
                            resultLabel.setStyle("-fx-text-fill: black;");
                        }));
                        timeline.setCycleCount(1); // 设置循环次数为 1
                        timeline.play();
                    }
                    myTurn = false;
                    updateTurnLabel();
                }
            }

        }
    }

    private void checkGameOver() {
        // 检查是否还有非零的单元格
        for (int[] row : game.board) {
            for (int cell : row) {
                if (cell != 0) {
                    return;
                }
            }
        }
        showGameOverDialog();
    }



    private void showGameOverDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText("Congratulations!");
        alert.setContentText("You've matched all pairs and completed the game!\nYour final score is: " + score);

        alert.showAndWait();
        handleReset(); // 重置游戏
    }

    public void updateScoreLabel(int score) {
        this.score = score;
        scoreLabel.setText(" " + score);
    }

    public void updateTurnLabel() {
        position[0] = 0;
        if(myTurn){
            turnLabel.setText("My Turn");
        }else {
            turnLabel.setText("Opponent Turn");
        }

    }

    public void updateResultLabel(String message) {
        resultLabel.setText(message);
    }

    @FXML
    private void handleReset() {
        this.score = 0;
        updateScoreLabel(this.score);
        game = new Game(Game.SetupBoard(game.row - 2, game.col -2));
        createGameBoard();

    }

    public ImageView addContent(int content){
        return switch (content) {
            case 0 -> new ImageView(imageCarambola);
            case 1 -> new ImageView(imageApple);
            case 2 -> new ImageView(imageMango);
            case 3 -> new ImageView(imageBlueberry);
            case 4 -> new ImageView(imageCherry);
            case 5 -> new ImageView(imageGrape);
            case 6 -> new ImageView(imageKiwi);
            case 7 -> new ImageView(imageOrange);
            case 8 -> new ImageView(imagePeach);
            case 9 -> new ImageView(imagePear);
            case 10 -> new ImageView(imagePineapple);
            case 11 -> new ImageView(imageWatermelon);
            default -> null;
        };
    }

    public static Image imageApple = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/apple.png")).toExternalForm());
    public static Image imageMango = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/mango.png")).toExternalForm());
    public static Image imageBlueberry = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/blueberry.png")).toExternalForm());
    public static Image imageCherry = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/cherry.png")).toExternalForm());
    public static Image imageGrape = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/grape.png")).toExternalForm());
    public static Image imageCarambola = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/carambola.png")).toExternalForm());
    public static Image imageKiwi = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/kiwi.png")).toExternalForm());
    public static Image imageOrange = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/orange.png")).toExternalForm());
    public static Image imagePeach = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/peach.png")).toExternalForm());
    public static Image imagePear = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/pear.png")).toExternalForm());
    public static Image imagePineapple = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/pineapple.png")).toExternalForm());
    public static Image imageWatermelon = new Image(Objects.requireNonNull(Game.class.getResource("/org/example/demo/watermelon.png")).toExternalForm());

}
