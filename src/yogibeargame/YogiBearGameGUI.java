package yogibeargame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class YogiBearGameGUI {

    private final int rows = 13;
    private final int columns = 13;
    private JLabel[][] grid;
    private JFrame frame;
    private JLabel infoLabel;
    private int playerRow = rows / 2;
    private int playerCol = columns / 2;
    private int score = 0;
    private int lives = 3;
    private Timer timer;
    private Timer rangerTimer;
    private int elapsedTime = 0;
    private int currentLevel = 1;
    private Point startingPosition; 
    private boolean yogiHasMoved = false;

    private final ImageIcon treeIcon = resizeIcon("assets/tree.png", 50, 50);
    private final ImageIcon mountainIcon = resizeIcon("assets/mountain.png", 50, 50);
    private final ImageIcon basketIcon = resizeIcon("assets/basket.png", 50, 50);
    private final ImageIcon yogiIcon = resizeIcon("assets/yogi.png", 50, 50);
    private final ImageIcon rangerIcon = resizeIcon("assets/ranger.png", 50, 50);

    private List<Ranger> rangers;
    private List<Point> baskets;
    private List<Point> trees;  
    private List<Point> mountains;   
    
    // Initializes the game GUI and sets up the grid and other components
    public YogiBearGameGUI() {
        frame = new JFrame("Yogi Bear Game");

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });

        frame.setLayout(new BorderLayout());

        infoLabel = new JLabel("Score: " + score + " | Lives: " + lives + " | Time: 0s");
        frame.add(infoLabel, BorderLayout.SOUTH);

        JPanel gridPanel = new JPanel(new GridLayout(rows, columns));
        grid = new JLabel[rows][columns];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                grid[i][j] = new JLabel();
                grid[i][j].setOpaque(true);
                grid[i][j].setBackground(Color.GREEN);
                grid[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK));
                gridPanel.add(grid[i][j]);
            }
        }

        trees = new ArrayList<>();
        mountains = new ArrayList<>();
        baskets = new ArrayList<>();
        rangers = new ArrayList<>();

        frame.add(gridPanel, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Game");
        JMenuItem resetMenuItem = new JMenuItem("Reset");
        resetMenuItem.addActionListener(e -> confirmReset());
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.addActionListener(e -> confirmExit());
        JMenuItem highScoresMenuItem = new JMenuItem("High Scores");
        highScoresMenuItem.addActionListener(e -> showHighScoreTable());
        menu.add(resetMenuItem);
        menu.add(highScoresMenuItem);
        menu.add(exitMenuItem);
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                movePlayer(e);
            }
        });

        startTimer();
        startRangerMovement();

        frame.setSize(800, 800);
        frame.setVisible(true);

        loadLevel("levels/level1.txt");
    }

    // Confirms exit from the game with the user
    private void confirmExit() {
        int choice = JOptionPane.showConfirmDialog(
            frame,
            "Are you sure you want to exit the game?",
            "Confirm Exit",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    // Confirms game reset with the user
    private void confirmReset() {
        int choice = JOptionPane.showConfirmDialog(
            frame,
            "Are you sure you want to reset the game?",
            "Confirm Reset",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            resetGame();
        }
    }

    // Moves the player on the grid based on user input
    private void movePlayer(KeyEvent e) {
        int newRow = playerRow;
        int newCol = playerCol;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: newRow--; break;
            case KeyEvent.VK_S: newRow++; break;
            case KeyEvent.VK_A: newCol--; break;
            case KeyEvent.VK_D: newCol++; break;
            default: return;
        }

        if (isWalkable(newRow, newCol)) {
            playerRow = newRow;
            playerCol = newCol;

            Point currentPos = new Point(playerRow, playerCol);
            if (baskets.contains(currentPos)) {
                baskets.remove(currentPos);
                score++; 
                checkLevelCompletion();
            }

            // Resume ranger movement if Yogi moves after losing a life
            if (!yogiHasMoved) {
                yogiHasMoved = true;
                if (rangerTimer != null && !rangerTimer.isRunning()) {
                    rangerTimer.start();
                }
            }
        }

        checkRangerProximity();
        updateGrid();
        updateInfoLabel();
    }

    // Checks if the current level is complete
    private void checkLevelCompletion() {
        if (baskets.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Level Complete!");
            currentLevel++;
            if (currentLevel <= 10) {
                loadLevel("levels/level" + currentLevel + ".txt");
            } else {
                generateRandomLevel();
            }
        }
    }

    // Determines if a cell on the grid is walkable
    private boolean isWalkable(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return false;
        return !(trees.stream().anyMatch(p -> p.equals(new Point(row, col))) || 
                 mountains.stream().anyMatch(p -> p.equals(new Point(row, col))));
    }

    // Checks if a ranger is adjacent to the player
    private void checkRangerProximity() {
        for (Ranger ranger : rangers) {
            boolean isAdjacent = 
                (ranger.row == playerRow && Math.abs(ranger.col - playerCol) == 1) || 
                (ranger.col == playerCol && Math.abs(ranger.row - playerRow) == 1);

            if (isAdjacent) {
                handleRangerCollision();
                return; 
            }
        }
    }

    // Handles collision with a ranger
    private void handleRangerCollision() {
        lives--;
        if (lives > 0) {
            JOptionPane.showMessageDialog(frame, "You lost a life! Lives remaining: " + lives);
            playerRow = startingPosition.x; 
            playerCol = startingPosition.y;  
            yogiHasMoved = false; 
            if (rangerTimer != null && rangerTimer.isRunning()) {
                rangerTimer.stop();  
            }
        } else {
            if (rangerTimer != null && rangerTimer.isRunning()) {
                rangerTimer.stop();
            }
            String playerName = JOptionPane.showInputDialog(frame, "Game Over! Enter your name:");
            saveHighScore(playerName, score);
            resetGame();
        }
    }

    // Resets the game state to its initial values
    private void resetGame() {
        score = 0;
        lives = 3;
        elapsedTime = 0;
        currentLevel = 1;
        yogiHasMoved = false;  
        loadLevel("levels/level1.txt");  

        if (rangerTimer != null && !rangerTimer.isRunning()) {
            startRangerMovement(); 
        }

        if (timer != null && timer.isRunning()) {
            timer.stop(); 
        }

        elapsedTime = 0; 
        startTimer();  
    }

    // Updates the grid display based on the current game state
    private void updateGrid() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                grid[i][j].setIcon(null);
                grid[i][j].setBackground(Color.GREEN);
            }
        }

        trees.forEach(p -> grid[p.x][p.y].setIcon(treeIcon));

        mountains.forEach(p -> grid[p.x][p.y].setIcon(mountainIcon));

        baskets.forEach(p -> grid[p.x][p.y].setIcon(basketIcon));

        rangers.forEach(r -> grid[r.row][r.col].setIcon(rangerIcon));

        grid[playerRow][playerCol].setIcon(yogiIcon);
    }

    // Updates the information label at the bottom of the game window
    private void updateInfoLabel() {
        infoLabel.setText("Score: " + score + " | Lives: " + lives + " | Time: " + elapsedTime + "s");
    }

    // Starts the game timer to track elapsed time
    private void startTimer() {
        timer = new Timer(1000, e -> {
            elapsedTime++;  
            updateInfoLabel();  
        });
        timer.start(); 
    }

    // Starts the ranger movement timer and handles their periodic movements
    private void startRangerMovement() {
        if (rangerTimer == null) { 
            rangerTimer = new Timer(500, e -> {
                for (Ranger ranger : rangers) {
                    ranger.move(rows, columns);
                }
                checkRangerProximity();
                updateGrid();
            });
        }

        // Start the timer if it hasn't already started
        if (yogiHasMoved && !rangerTimer.isRunning()) {
            rangerTimer.start();
        }
    }

    // Loads a level from a specified file and initializes game elements
    private void loadLevel(String filename) {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        elapsedTime = 0;

        trees.clear();
        mountains.clear();
        baskets.clear();
        rangers.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                for (int col = 0; col < line.length(); col++) {
                    char cell = line.charAt(col);
                    Point position = new Point(row, col);

                    switch (cell) {
                        case 'O': trees.add(position); break;      
                        case 'M': mountains.add(position); break;   
                        case 'B': baskets.add(position); break;  
                        case 'R': rangers.add(new Ranger(row, col, "horizontal")); break;
                        case 'Y': 
                            playerRow = row;
                            playerCol = col;
                            startingPosition = new Point(row, col); 
                            break;
                    }
                }
                row++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error loading level: " + filename);
        }

        updateGrid();
        updateInfoLabel();
        startTimer();
    }

    // Generates a random level layout when predefined levels are completed
    private void generateRandomLevel() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        elapsedTime = 0;

        trees.clear();
        mountains.clear();
        baskets.clear();
        rangers.clear();

        while (baskets.size() < 4) {
            Point basket = generateRandomPoint();
            if (!baskets.contains(basket) && !basket.equals(new Point(playerRow, playerCol))) {
                baskets.add(basket);
            }
        }

        while (trees.size() < 4) {
            Point tree = generateRandomPoint();
            if (!trees.contains(tree) && !baskets.contains(tree)) {
                trees.add(tree);
            }
        }

        while (mountains.size() < 4) {
            Point mountain = generateRandomPoint();
            if (!mountains.contains(mountain) && !baskets.contains(mountain)) {
                mountains.add(mountain);
            }
        }

        while (rangers.size() < 4) {
            Point rangerPos = generateRandomPoint();
            if (!trees.contains(rangerPos) && !mountains.contains(rangerPos) && !baskets.contains(rangerPos)) {
                String direction = Math.random() < 0.5 ? "horizontal" : "vertical";
                rangers.add(new Ranger(rangerPos.x, rangerPos.y, direction));
            }
        }

        updateGrid();
        updateInfoLabel();
        startTimer();
    }

    // Generates a random point within the grid boundaries
    private Point generateRandomPoint() {
        int row = (int) (Math.random() * rows);
        int col = (int) (Math.random() * columns);
        return new Point(row, col);
    }

    // Saves the high score to a file
    private void saveHighScore(String playerName, int score) {
        String query = "INSERT INTO HighScores (playerName, score) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, playerName);
            pstmt.setInt(2, score);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error saving high score.");
        }
    }


    // Loads high scores from a file and sorts them
    private List<String> loadHighScores() {
        List<String> scores = new ArrayList<>();
        String query = "SELECT playerName, score FROM HighScores ORDER BY score DESC LIMIT 10";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String entry = rs.getString("playerName") + ": " + rs.getInt("score");
                scores.add(entry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error loading high scores.");
        }

        return scores;
    }


    // Displays the high score table in a dialog
    private void showHighScoreTable() {
        List<String> scores = loadHighScores();
        StringBuilder sb = new StringBuilder("High Scores:\n");
        for (String score : scores) {
            sb.append(score).append("\n");
        }
        JOptionPane.showMessageDialog(frame, sb.toString());
    }


    // Resizes an image icon to specified dimensions
    private ImageIcon resizeIcon(String path, int width, int height) {
        ImageIcon originalIcon = new ImageIcon(path);
        Image resizedImage = originalIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }

    // Entry point for the program
    public static void main(String[] args) {
        YogiBearGameGUI yogiBearGameGUI = new YogiBearGameGUI();
    }

    // Represents a Ranger character that moves on the grid
    private class Ranger {
        int row, col;
        String direction;
        boolean movingForward = true;

        Ranger(int row, int col, String direction) {
            this.row = row;
            this.col = col;
            this.direction = direction;
        }

        // Moves the ranger within the grid boundaries
        void move(int maxRows, int maxCols) {
            int nextRow = row;
            int nextCol = col;

            if (direction.equals("horizontal")) {
                nextCol += movingForward ? 1 : -1;
                if (nextCol >= 0 && nextCol < maxCols && isWalkable(nextRow, nextCol)) {
                    col = nextCol; 
                } else {
                    movingForward = !movingForward; 
                }
            } else if (direction.equals("vertical")) {
                nextRow += movingForward ? 1 : -1;
                if (nextRow >= 0 && nextRow < maxRows && isWalkable(nextRow, nextCol)) {
                    row = nextRow;  
                } else {
                    movingForward = !movingForward;
                }
            }
        }
    }
    
}
