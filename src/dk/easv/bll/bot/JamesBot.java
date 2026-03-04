package dk.easv.bll.bot;

import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JamesBot implements IBot {
    private static final String BOTNAME = "James's Bot";
    private String myPlayerId;
    private String oppPlayerId;

    // Time management for Iterative Deepening
    private long startTime;
    private long timeLimitMs;
    private boolean outOfTime;

    // Fast mapping to check wins inside a 3x3 local board stored in a 1D array
    private static final int[] LOCAL_OFFSETS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[][] WIN_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Cols
            {0, 4, 8}, {2, 4, 6}             // Diagonals
    };

    @Override
    public String getBotName() {
        return BOTNAME;
    }

    @Override
    public IMove doMove(IGameState state) {
        startTime = System.currentTimeMillis();
        // Set to 800ms to stay safely under a typical 1000ms max time limit
        timeLimitMs = 800;
        outOfTime = false;

        // 1. Identify who we are playing as
        myPlayerId = state.getMoveNumber() % 2 == 0 ? "0" : "1";
        oppPlayerId = myPlayerId.equals("0") ? "1" : "0";

        List<IMove> availableMoves = state.getField().getAvailableMoves();
        if (availableMoves.isEmpty()) return null;
        if (availableMoves.size() == 1) return availableMoves.get(0);

        // 2. Translate the slow API state into our blazing fast 1D simulation board
        FastBoard board = new FastBoard(state, availableMoves, myPlayerId, oppPlayerId);

        // 3. Run the Iterative Deepening Minimax Engine
        int absoluteBestMoveIndex = -1;
        int depth = 1;

        while (!outOfTime) {
            int currentBestMove = findBestMoveAtDepth(board, depth);
            if (!outOfTime && currentBestMove != -1) {
                absoluteBestMoveIndex = currentBestMove;
            }
            depth++;
            if (depth > 20) break; // Safety cap
        }

        // 4. Translate the best integer move back to an IMove for the game engine
        if (absoluteBestMoveIndex != -1) {
            int targetX = absoluteBestMoveIndex % 9;
            int targetY = absoluteBestMoveIndex / 9;
            for (IMove m : availableMoves) {
                if (m.getX() == targetX && m.getY() == targetY) {
                    return m;
                }
            }
        }

        // Fallback (should never be reached unless time limit is too short for depth 1)
        return availableMoves.get((int) (Math.random() * availableMoves.size()));
    }

    // --- CORRECTED MINIMAX ENGINE ---

    // Top-level search: Returns the actual INDEX of the best move
    private int findBestMoveAtDepth(FastBoard board, int depth) {
        List<Integer> legalMoves = board.generateLegalMoves();
        if (legalMoves.isEmpty()) return -1;

        // UNPREDICTABILITY: Shuffle moves to break ties randomly
        Collections.shuffle(legalMoves);

        int bestMoveIndex = -1;
        int bestScore = Integer.MIN_VALUE + 1;
        int alpha = Integer.MIN_VALUE + 1;
        int beta = Integer.MAX_VALUE - 1;

        for (int moveIndex : legalMoves) {
            int prevActiveBoard = board.activeBoard;
            int localBoardIndex = (moveIndex % 9 / 3) + (moveIndex / 9 / 3) * 3;
            int prevMacroState = board.macroGrid[localBoardIndex];

            // Bot plays (player 1)
            board.makeMove(moveIndex, 1, localBoardIndex);
            // Search down the tree from Opponent's perspective (minimizing)
            int score = searchAux(board, depth - 1, alpha, beta, false);
            board.undoMove(moveIndex, prevActiveBoard, localBoardIndex, prevMacroState);

            if (outOfTime) return -1;

            if (score > bestScore) {
                bestScore = score;
                bestMoveIndex = moveIndex;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return bestMoveIndex;
    }

    // Recursive search: Returns the SCORE of the board state
    private int searchAux(FastBoard board, int depth, int alpha, int beta, boolean isMaximizing) {
        if (System.currentTimeMillis() - startTime > timeLimitMs) {
            outOfTime = true;
            return 0;
        }

        int boardStatus = board.checkMacroWin();
        if (boardStatus != 0 || depth == 0) {
            return (boardStatus != 0) ? boardStatus * 10000 : evaluate(board);
        }

        List<Integer> legalMoves = board.generateLegalMoves();
        if (legalMoves.isEmpty()) return 0; // Draw

        int bestScore = isMaximizing ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;
        int player = isMaximizing ? 1 : -1;

        for (int moveIndex : legalMoves) {
            int prevActiveBoard = board.activeBoard;
            int localBoardIndex = (moveIndex % 9 / 3) + (moveIndex / 9 / 3) * 3;
            int prevMacroState = board.macroGrid[localBoardIndex];

            board.makeMove(moveIndex, player, localBoardIndex);
            int score = searchAux(board, depth - 1, alpha, beta, !isMaximizing);
            board.undoMove(moveIndex, prevActiveBoard, localBoardIndex, prevMacroState);

            if (outOfTime) return 0;

            if (isMaximizing) {
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, bestScore);
            } else {
                bestScore = Math.min(bestScore, score);
                beta = Math.min(beta, bestScore);
            }
            if (beta <= alpha) break; // Prune
        }
        return bestScore;
    }

    // --- PATTERN RECOGNITION ---

    private int evaluate(FastBoard board) {
        int score = 0;

        for (int i = 0; i < 9; i++) {
            if (board.macroGrid[i] == 1) score += 500;
            else if (board.macroGrid[i] == -1) score -= 500;
        }

        for (int i = 0; i < 81; i++) {
            if (board.grid[i] != 0 && board.macroGrid[(i % 9 / 3) + (i / 9 / 3) * 3] == 0) {
                int pieceValue = board.grid[i];
                int localIndex = (i % 9 % 3) + (i / 9 % 3) * 3;

                if (localIndex == 4) score += pieceValue * 15;

                else if (localIndex % 2 == 0) score += pieceValue * 5;
            }
        }

        int noise = (int) (Math.random() * 5) - 2;
        return score + noise;
    }


    private class FastBoard {
        public int[] grid = new int[81];       // 0 = empty, 1 = Bot, -1 = Opponent
        public int[] macroGrid = new int[9];   // State of the 9 local boards
        public int activeBoard = -1;           // -1 means any non-full board is valid

        public FastBoard(IGameState state, List<IMove> availableMoves, String myId, String oppId) {
            String[][] rawBoard = state.getField().getBoard();

            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 9; y++) {
                    String val = rawBoard[x][y];
                    int index = x + y * 9;
                    if (myId.equals(val)) grid[index] = 1;
                    else if (oppId.equals(val)) grid[index] = -1;
                    else grid[index] = 0;
                }
            }

            for (int b = 0; b < 9; b++) {
                macroGrid[b] = checkLocalWinState(b);
            }

            activeBoard = -1;
            if (!availableMoves.isEmpty()) {
                IMove first = availableMoves.get(0);
                int expectedBoard = (first.getX() / 3) + (first.getY() / 3) * 3;
                boolean allSame = true;
                for (IMove m : availableMoves) {
                    int b = (m.getX() / 3) + (m.getY() / 3) * 3;
                    if (b != expectedBoard) {
                        allSame = false;
                        break;
                    }
                }
                if (allSame) activeBoard = expectedBoard;
            }
        }

        public List<Integer> generateLegalMoves() {
            List<Integer> moves = new ArrayList<>();
            boolean freePlay = (activeBoard == -1 || macroGrid[activeBoard] != 0 || isBoardFull(activeBoard));

            for (int i = 0; i < 81; i++) {
                int localBoardIndex = (i % 9 / 3) + (i / 9 / 3) * 3;
                if (grid[i] == 0 && macroGrid[localBoardIndex] == 0) {
                    if (freePlay || localBoardIndex == activeBoard) {
                        moves.add(i);
                    }
                }
            }
            return moves;
        }

        public void makeMove(int moveIndex, int player, int localBoardIndex) {
            grid[moveIndex] = player;

            if (macroGrid[localBoardIndex] == 0 && checkLocalWin(localBoardIndex, player)) {
                macroGrid[localBoardIndex] = player;
            }

            int localX = moveIndex % 9 % 3;
            int localY = moveIndex / 9 % 3;
            activeBoard = localX + localY * 3;
        }

        public void undoMove(int moveIndex, int prevActiveBoard, int localBoardIndex, int prevMacroState) {
            grid[moveIndex] = 0;
            macroGrid[localBoardIndex] = prevMacroState;
            activeBoard = prevActiveBoard;
        }

        private boolean checkLocalWin(int boardIndex, int player) {
            int startX = (boardIndex % 3) * 3;
            int startY = (boardIndex / 3) * 3;
            int offset = startX + startY * 9;

            for (int[] line : WIN_LINES) {
                if (grid[offset + LOCAL_OFFSETS[line[0]]] == player &&
                        grid[offset + LOCAL_OFFSETS[line[1]]] == player &&
                        grid[offset + LOCAL_OFFSETS[line[2]]] == player) {
                    return true;
                }
            }
            return false;
        }

        private int checkLocalWinState(int boardIndex) {
            if (checkLocalWin(boardIndex, 1)) return 1;
            if (checkLocalWin(boardIndex, -1)) return -1;
            return 0;
        }

        public int checkMacroWin() {
            for (int[] line : WIN_LINES) {
                if (macroGrid[line[0]] != 0 &&
                        macroGrid[line[0]] == macroGrid[line[1]] &&
                        macroGrid[line[0]] == macroGrid[line[2]]) {
                    return macroGrid[line[0]];
                }
            }
            return 0;
        }

        private boolean isBoardFull(int boardIndex) {
            int startX = (boardIndex % 3) * 3;
            int startY = (boardIndex / 3) * 3;
            int offset = startX + startY * 9;
            for (int i = 0; i < 9; i++) {
                if (grid[offset + LOCAL_OFFSETS[i]] == 0) return false;
            }
            return true;
        }
    }
}