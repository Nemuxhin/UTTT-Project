package dk.easv.bll.bot;

import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.List;
import java.util.Random;

public class BobBot implements IBot {

    private final Random rng = new Random();

    @Override
    public String getBotName() {
        return "BobBot";
    }

    @Override
    public IMove doMove(IGameState state) {
        if (state == null || state.getField() == null) return null;

        List<IMove> moves = state.getField().getAvailableMoves();
        if (moves == null || moves.isEmpty()) return null;

        String[][] board = state.getField().getBoard();
        if (board == null) return moves.get(rng.nextInt(moves.size()));

        int myId = state.getMoveNumber() % 2;
        String me = String.valueOf(myId);
        String opp = String.valueOf(1 - myId);

        // 1) Try to win immediately
        for (IMove m : moves) {
            if (completesLine(board, m, me)) return m;
        }

        // 2) Block the opponent if they are about to win
        for (IMove m : moves) {
            if (completesLine(board, m, opp)) return m;
        }

        // 3) Take the center of the microboard if available
        for (IMove m : moves) {
            if (m.getX() % 3 == 1 && m.getY() % 3 == 1) return m;
        }

        // 4) Take a corner of the microboard if available
        for (IMove m : moves) {
            int lx = m.getX() % 3;
            int ly = m.getY() % 3;
            if ((lx == 0 || lx == 2) && (ly == 0 || ly == 2)) return m;
        }

        // 5) Fall back to a random move
        return moves.get(rng.nextInt(moves.size()));
    }

    // Check if placing 'player' at this move completes a row, column, or diagonal
    private boolean completesLine(String[][] board, IMove move, String player) {
        int x = move.getX();
        int y = move.getY();

        int startX = (x / 3) * 3;
        int startY = (y / 3) * 3;

        int lx = x - startX;
        int ly = y - startY;

        // Horizontal row (same y)
        if (owns(board, startX + 0, y, x, y, player) &&
                owns(board, startX + 1, y, x, y, player) &&
                owns(board, startX + 2, y, x, y, player)) return true;

        // Vertical column (same x)
        if (owns(board, x, startY + 0, x, y, player) &&
                owns(board, x, startY + 1, x, y, player) &&
                owns(board, x, startY + 2, x, y, player)) return true;

        // Main diagonal
        if (lx == ly &&
                owns(board, startX + 0, startY + 0, x, y, player) &&
                owns(board, startX + 1, startY + 1, x, y, player) &&
                owns(board, startX + 2, startY + 2, x, y, player)) return true;

        // Anti-diagonal
        if (lx + ly == 2 &&
                owns(board, startX + 2, startY + 0, x, y, player) &&
                owns(board, startX + 1, startY + 1, x, y, player) &&
                owns(board, startX + 0, startY + 2, x, y, player)) return true;

        return false;
    }

    // True if the cell already belongs to the player, OR it is the move we are about to play
    private boolean owns(String[][] board, int cellX, int cellY, int moveX, int moveY, String player) {
        if (cellX == moveX && cellY == moveY) return true;
        return player.equals(board[cellX][cellY]);
    }
}