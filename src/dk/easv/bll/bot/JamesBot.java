package dk.easv.bll.bot;

import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.*;

/**
 * JamesBot - Ultimate Tic-Tac-Toe AI
 *
 * Strategy:
 * - Iterative deepening minimax with alpha-beta pruning
 * - Smart move ordering (winning moves > center > strategic > rest)
 * - Rich evaluation: macro threats, local threats, board-send tactics
 * - Slight human-like noise to avoid being obviously robotic
 */
public class JamesBot implements IBot {
    private static final String BOTNAME = "JamesBot";

    private String myPlayerId;
    private String oppPlayerId;
    private long startTime;
    private long timeLimitMs;
    private boolean outOfTime;
    private Random rng = new Random();

    private static final int[] LOCAL_OFFSETS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[][] WIN_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    // Strategic value of each position on a 3x3 board (center > corners > edges)
    private static final int[] MACRO_POS_VALUE = {3, 2, 3, 2, 4, 2, 3, 2, 3};
    private static final int[] LOCAL_POS_VALUE  = {3, 2, 3, 2, 5, 2, 3, 2, 3};

    @Override
    public String getBotName() { return BOTNAME; }

    @Override
    public IMove doMove(IGameState state) {
        startTime  = System.currentTimeMillis();
        timeLimitMs = 850;
        outOfTime  = false;

        myPlayerId  = state.getMoveNumber() % 2 == 0 ? "0" : "1";
        oppPlayerId = myPlayerId.equals("0") ? "1" : "0";

        List<IMove> availableMoves = state.getField().getAvailableMoves();
        if (availableMoves.isEmpty()) return null;
        if (availableMoves.size() == 1) return availableMoves.get(0);

        FastBoard board = new FastBoard(state, availableMoves, myPlayerId, oppPlayerId);

        int absoluteBestMoveIndex = -1;
        int depth = 1;

        while (!outOfTime) {
            int currentBestMove = findBestMoveAtDepth(board, depth);
            if (!outOfTime && currentBestMove != -1) {
                absoluteBestMoveIndex = currentBestMove;
            }
            depth++;
            if (depth > 18) break;
        }

        if (absoluteBestMoveIndex != -1) {
            int targetX = absoluteBestMoveIndex % 9;
            int targetY = absoluteBestMoveIndex / 9;
            for (IMove m : availableMoves) {
                if (m.getX() == targetX && m.getY() == targetY) return m;
            }
        }

        return availableMoves.get(rng.nextInt(availableMoves.size()));
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private int findBestMoveAtDepth(FastBoard board, int depth) {
        List<Integer> legalMoves = board.generateLegalMoves();
        if (legalMoves.isEmpty()) return -1;

        orderMoves(board, legalMoves, 1);

        int bestMoveIndex = -1;
        int bestScore = Integer.MIN_VALUE + 1;
        int alpha = Integer.MIN_VALUE + 1;
        int beta  = Integer.MAX_VALUE - 1;

        for (int moveIndex : legalMoves) {
            int prevActive    = board.activeBoard;
            int lbi           = lboardIndex(moveIndex);
            int prevMacroState = board.macroGrid[lbi];

            board.makeMove(moveIndex, 1, lbi);
            int score = searchAux(board, depth - 1, alpha, beta, false);
            board.undoMove(moveIndex, prevActive, lbi, prevMacroState);

            if (outOfTime) return -1;

            if (score > bestScore) {
                bestScore = score;
                bestMoveIndex = moveIndex;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return bestMoveIndex;
    }

    private int searchAux(FastBoard board, int depth, int alpha, int beta, boolean isMax) {
        if (System.currentTimeMillis() - startTime > timeLimitMs) {
            outOfTime = true;
            return 0;
        }

        int boardStatus = board.checkMacroWin();
        if (boardStatus != 0) return boardStatus * (10000 + depth); // prefer faster wins/blocks

        if (depth == 0) return evaluate(board);

        List<Integer> legalMoves = board.generateLegalMoves();
        if (legalMoves.isEmpty()) return 0;

        int player = isMax ? 1 : -1;
        orderMoves(board, legalMoves, player);

        int bestScore = isMax ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;

        for (int moveIndex : legalMoves) {
            int prevActive     = board.activeBoard;
            int lbi            = lboardIndex(moveIndex);
            int prevMacroState = board.macroGrid[lbi];

            board.makeMove(moveIndex, player, lbi);
            int score = searchAux(board, depth - 1, alpha, beta, !isMax);
            board.undoMove(moveIndex, prevActive, lbi, prevMacroState);

            if (outOfTime) return 0;

            if (isMax) { bestScore = Math.max(bestScore, score); alpha = Math.max(alpha, bestScore); }
            else       { bestScore = Math.min(bestScore, score); beta  = Math.min(beta,  bestScore); }

            if (beta <= alpha) break;
        }
        return bestScore;
    }

    // ── Move ordering ────────────────────────────────────────────────────────

    private void orderMoves(FastBoard board, List<Integer> moves, int player) {
        moves.sort((a, b) -> Integer.compare(quickScore(board, b, player), quickScore(board, a, player)));
    }

    private int quickScore(FastBoard board, int moveIndex, int player) {
        int lbi = lboardIndex(moveIndex);

        // Check if this move wins the local board
        board.grid[moveIndex] = player;
        boolean winsLocal = board.checkLocalWin(lbi, player);
        board.grid[moveIndex] = 0;

        int score = LOCAL_POS_VALUE[moveIndex % 9 % 3 + (moveIndex / 9 % 3) * 3]
                + MACRO_POS_VALUE[lbi];

        if (winsLocal) score += 1000;

        // Bonus: sending opponent to a dead board (won or full) gives us free choice next turn
        int nextBoard = (moveIndex % 9 % 3) + (moveIndex / 9 % 3) * 3;
        if (board.macroGrid[nextBoard] != 0 || board.isBoardFull(nextBoard)) score += 30;

        return score;
    }

    // ── Evaluation ───────────────────────────────────────────────────────────

    private int evaluate(FastBoard board) {
        int score = 0;

        // Macro-board won sub-boards
        for (int i = 0; i < 9; i++) {
            if      (board.macroGrid[i] ==  1) score += 500 + MACRO_POS_VALUE[i] * 30;
            else if (board.macroGrid[i] == -1) score -= 500 + MACRO_POS_VALUE[i] * 30;
        }

        // Macro-level threats (2-in-a-line)
        for (int[] line : WIN_LINES) {
            int my = 0, opp = 0, empty = 0;
            for (int idx : line) {
                if      (board.macroGrid[idx] ==  1) my++;
                else if (board.macroGrid[idx] == -1) opp++;
                else empty++;
            }
            if (opp == 0 && my == 2 && empty == 1) score += 200;
            if (my  == 0 && opp == 2 && empty == 1) score -= 250;
        }

        // Local board evaluation
        for (int b = 0; b < 9; b++) {
            if (board.macroGrid[b] != 0) continue;
            score += evaluateLocal(board, b);
        }

        // Human-like noise: small random jitter so bot varies its play slightly
        score += rng.nextInt(9) - 4;

        return score;
    }

    private int evaluateLocal(FastBoard board, int boardIndex) {
        int score = 0;
        int startX = (boardIndex % 3) * 3;
        int startY = (boardIndex / 3) * 3;
        int offset = startX + startY * 9;

        // Positional values
        for (int i = 0; i < 9; i++) {
            int cell = board.grid[offset + LOCAL_OFFSETS[i]];
            if      (cell ==  1) score += LOCAL_POS_VALUE[i];
            else if (cell == -1) score -= LOCAL_POS_VALUE[i];
        }

        // Local 2-in-a-row threats
        for (int[] line : WIN_LINES) {
            int my = 0, opp = 0;
            for (int idx : line) {
                int cell = board.grid[offset + LOCAL_OFFSETS[idx]];
                if      (cell ==  1) my++;
                else if (cell == -1) opp++;
            }
            if (opp == 0) {
                if (my == 2) score += 20 + MACRO_POS_VALUE[boardIndex] * 3;
                if (my == 1) score += 4;
            }
            if (my == 0) {
                if (opp == 2) score -= 25 + MACRO_POS_VALUE[boardIndex] * 3;
                if (opp == 1) score -= 4;
            }
        }
        return score;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static int lboardIndex(int moveIndex) {
        return (moveIndex % 9 / 3) + (moveIndex / 9 / 3) * 3;
    }

    // ── FastBoard ─────────────────────────────────────────────────────────────

    private class FastBoard {
        public int[] grid      = new int[81];
        public int[] macroGrid = new int[9];
        public int activeBoard = -1;

        public FastBoard(IGameState state, List<IMove> availableMoves, String myId, String oppId) {
            String[][] rawBoard = state.getField().getBoard();
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 9; y++) {
                    String val = rawBoard[x][y];
                    int index = x + y * 9;
                    if      (myId.equals(val))  grid[index] =  1;
                    else if (oppId.equals(val)) grid[index] = -1;
                }
            }

            for (int b = 0; b < 9; b++) {
                if      (checkLocalWin(b,  1)) macroGrid[b] =  1;
                else if (checkLocalWin(b, -1)) macroGrid[b] = -1;
            }

            activeBoard = -1;
            if (!availableMoves.isEmpty()) {
                IMove first = availableMoves.get(0);
                int expected = (first.getX() / 3) + (first.getY() / 3) * 3;
                boolean allSame = true;
                for (IMove m : availableMoves) {
                    if ((m.getX() / 3) + (m.getY() / 3) * 3 != expected) { allSame = false; break; }
                }
                if (allSame) activeBoard = expected;
            }
        }

        public List<Integer> generateLegalMoves() {
            List<Integer> moves = new ArrayList<>();
            boolean free = (activeBoard == -1 || macroGrid[activeBoard] != 0 || isBoardFull(activeBoard));
            for (int i = 0; i < 81; i++) {
                int lbi = lboardIndex(i);
                if (grid[i] == 0 && macroGrid[lbi] == 0 && (free || lbi == activeBoard)) {
                    moves.add(i);
                }
            }
            return moves;
        }

        public void makeMove(int moveIndex, int player, int lbi) {
            grid[moveIndex] = player;
            if (macroGrid[lbi] == 0 && checkLocalWin(lbi, player)) macroGrid[lbi] = player;
            activeBoard = (moveIndex % 9 % 3) + (moveIndex / 9 % 3) * 3;
        }

        public void undoMove(int moveIndex, int prevActiveBoard, int lbi, int prevMacroState) {
            grid[moveIndex] = 0;
            macroGrid[lbi] = prevMacroState;
            activeBoard = prevActiveBoard;
        }

        public boolean checkLocalWin(int boardIndex, int player) {
            int startX = (boardIndex % 3) * 3;
            int startY = (boardIndex / 3) * 3;
            int offset = startX + startY * 9;
            for (int[] line : WIN_LINES) {
                if (grid[offset + LOCAL_OFFSETS[line[0]]] == player &&
                        grid[offset + LOCAL_OFFSETS[line[1]]] == player &&
                        grid[offset + LOCAL_OFFSETS[line[2]]] == player) return true;
            }
            return false;
        }

        public int checkMacroWin() {
            for (int[] line : WIN_LINES) {
                if (macroGrid[line[0]] != 0 &&
                        macroGrid[line[0]] == macroGrid[line[1]] &&
                        macroGrid[line[0]] == macroGrid[line[2]]) return macroGrid[line[0]];
            }
            return 0;
        }

        public boolean isBoardFull(int boardIndex) {
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