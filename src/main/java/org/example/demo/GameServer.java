package org.example.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;




public class GameServer {
    public static void main(String[] args) {
        Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
        List<ClientHandler> waitingList = Collections.synchronizedList(new ArrayList<>());


        try (ServerSocket serverSocket = new ServerSocket(1234)) {
            System.out.println("Server started ......");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected.");

                ClientHandler clientHandler = new ClientHandler(socket, clients, waitingList);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final Set<ClientHandler> clients;
    private String clientName;
    private List<ClientHandler> waitingList;
    private Game game;
    private ClientHandler opponent;
    private int score = 0;

    public ClientHandler(Socket socket, Set<ClientHandler> clients, List<ClientHandler> waitingList) throws IOException {
        this.socket = socket;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.waitingList = waitingList;
    }

    public void sendMessage(String message) {
        System.out.println("Sending message to " + clientName + ": " + message);
        out.println(message);
        out.flush();
    }

    public void sendStatus(){
        sendMessage("Status");
        StringBuilder message = new StringBuilder();
        int[][] board = game.board;
        for (int i = 0; i < game.row; i++) {
            for (int j = 0; j < game.col; j++) {
                message.append(board[i][j]).append(" ");
            }
        }
        sendMessage(message.toString());
        // send score
        sendMessage(String.valueOf(score));
        // send opponent score
        sendMessage(String.valueOf(opponent.score));
    }

    @Override
    public void run() {
        try {
            clientName = in.readLine();

            int height = Integer.parseInt(in.readLine());
            int width = Integer.parseInt(in.readLine());

            // System.out.println(STR."\{clientName} chose board size: \{height} x \{width}");

            game = new Game(Game.SetupBoard(height, width));

            // find a player to play with same board size
            for (ClientHandler player : waitingList) {
                if (player.game.row == game.row && player.game.col == game.col) {
                    System.out.println("Matched with " + player.clientName + " size " + height + " x " + width);
                    opponent = player;
                    opponent.sendMessage("Start");
                    opponent.opponent = this;
                    this.sendMessage("Start");
                    waitingList.remove(player);
                    // check game is valid
                    validateGame();
                    opponent.game = game;
                    // start the game
                    sendStatus();
                    opponent.sendStatus();

                    // pick a random player to start
                    boolean myTurn = false;
                    if (Math.random() < 0.5) {
                        sendMessage("Your Turn");
                        opponent.sendMessage("Opponent Turn");
                        myTurn = true;
                    } else {
                        opponent.sendMessage("Your Turn");
                        sendMessage("Opponent Turn");
                    }
                    while(checkGame()){
                        System.out.println("--------------------------------------");
                        if (myTurn) {
                            String validMessage = "";
                            try{
                                validMessage = in.readLine();
                            }catch (Exception e) {
                                opponent.sendMessage("Opponent Loss");
                            }
                            System.out.println("Valid message: " + validMessage);
                            if(validMessage == null) {
                                opponent.sendMessage("Opponent Loss");
//                                waitingList.add(opponent);
                                opponent.socket.close();
                                clients.remove(this);
                                clients.remove(opponent);
                                return;
                            }else if (validMessage.equals("Valid")) {
                                String newBoardString = in.readLine();
                                String[] newBoard = newBoardString.split(" ");
                                System.out.println("New board: " + newBoardString);
                                for (int i = 0; i < game.row; i++) {
                                    for (int j = 0; j < game.col; j++) {
                                        game.board[i][j] = Integer.parseInt(newBoard[i * game.col + j]);
                                    }
                                }
                                score += 10;
                            } else if(validMessage.equals("Invalid")) {
                                opponent.score += 5;
                            } else if (validMessage.equals("Loss")) {
                                opponent.sendMessage("Opponent Loss");
//                                waitingList.add(opponent);
                                opponent.socket.close();
                                clients.remove(this);
                                clients.remove(opponent);
                                return;
                            }
                            // update the board status
                            sendStatus();
//                            System.out.println(opponent.clientName + " connect status" + opponent.socket.isConnected());
//                            System.out.println(this.clientName + " connect status" + socket.isConnected());
                            try{
                                opponent.sendStatus();
                            } catch (Exception e){
                                sendMessage("Opponent Loss");
                                return;
                            }
//                            opponent.sendStatus();

                            // switch turn
                            myTurn = false;
                            opponent.sendMessage("Your Turn");
                            sendMessage("Opponent Turn");

                        }else {
                            String validMessage = "";
                            try{
                                validMessage = opponent.in.readLine();
                            }catch (Exception e) {
                                sendMessage("Opponent Loss");
                                return;
                            }

                            System.out.println("Valid message: " + validMessage);
                            if(validMessage == null) {
                                this.sendMessage("Opponent Loss");
//                                waitingList.add(this);
                                socket.close();
                                clients.remove(this);
                                clients.remove(opponent);
                                return;
                            } else if (validMessage.equals("Valid")) {
                                String newBoardString = opponent.in.readLine();
                                String[] newBoard = newBoardString.split(" ");
                                System.out.println("New board: " + newBoardString);
                                for (int i = 0; i < game.row; i++) {
                                    for (int j = 0; j < game.col; j++) {
                                        game.board[i][j] = Integer.parseInt(newBoard[i * game.col + j]);
                                    }
                                }
                                opponent.score += 10;
                            } else if(validMessage.equals("Invalid")) {
                                score += 5;
                            } else if (validMessage.equals("Loss")) {
                                this.sendMessage("Opponent Loss");
//                                waitingList.add(this);
                                socket.close();
                                clients.remove(this);
                                clients.remove(opponent);
                                return;
                            }

                            // update the board status
//                            System.out.println(this.clientName +" connect status" + socket.isConnected());
//                            System.out.println(opponent.clientName + " connect status" + opponent.socket.isConnected());
                            try {
                                sendStatus();
                            } catch (Exception e){
                                opponent.sendMessage("Opponent Loss");
//                                return;
                            }
//                            sendStatus();
                            opponent.sendStatus();

                            // switch turn
                            myTurn = true;

                            sendMessage("Your Turn");
                            opponent.sendMessage("Opponent Turn");

                        }
                    }
                    // game over
                    if (score > opponent.score) {
                        sendMessage("Win");
                        opponent.sendMessage("Lose");
                    } else if (score < opponent.score) {
                        sendMessage("Lose");
                        opponent.sendMessage("Win");
                    } else {
                        sendMessage("Draw");
                        opponent.sendMessage("Draw");
                    }
//                    socket.close();
//                    opponent.socket.close();
                    clients.remove(this);
                    clients.remove(opponent);
                    return;
                }else {
                    System.out.println("Unmatched with " + player.clientName + "size" + player.game.row + "x" + player.game.col);
                }
            }
            waitingList.add(this);
            out.println("Wait");

            new Thread(() -> {
                waitProtect();
                waitingList.remove(this);
            }).start();
        } catch (IOException e) {
            System.out.println("disconnected");
            System.out.println(e.getMessage()
            );

        } finally {
//            try {
//                socket.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            clients.remove(this);
        }
    }

    public boolean checkGame(){
        for (int i = 1; i < game.row - 1; i++) {
            for (int j = 1; j < game.col - 1; j++) {
                for (int k = 0; k < game.row - 1; k++) {
                    for (int l = 0; l < game.col - 1; l++) {
                        if( i == k && j == l) continue;
                        if (game.judge(i, j, k, l)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void validateGame(){
        while (!checkGame()) {
            game = new Game(Game.SetupBoard(game.row - 2, game.col - 2));
        }
    }

    public void waitProtect(){
        String message;
        System.out.println("Waiting Protect");
        try {
            if ((message = in.readLine()) == null) {
                System.out.println("ERROR: "+ clientName +" Disconnected");
            }
        } catch (IOException e) {
            System.out.println("Error reading message: " + e.getMessage());
        }

    }

}
