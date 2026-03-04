package dk.easv.bll.bot;

import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.Arrays;
import java.util.List;

public class BobBot implements IBot {

    @Override
    public IMove doMove(IGameState state) {

        List<IMove> moves = state.getField().getAvailableMoves();
        String myId = getMyPlayerId(state);
        String opponent = myId.equals("0") ? "1" : "0";

        IMove bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (IMove move : moves) {

            int score = 0;

            // Win
            if (isWinningMove(state, move, myId))
                score += 5000;

            // Block
            if (isWinningMove(state, move, opponent))
                score += 4000;

            // Create threat (2 in row)
            score += countTwoInRow(state, move, myId) * 200;

            // Block opponent threat
            score += countTwoInRow(state, move, opponent) * 150;

            // Center microboard
            if (move.getX() % 3 == 1 && move.getY() % 3 == 1)
                score += 50;

            // Corner microboard
            if ((move.getX() % 3 == 0 || move.getX() % 3 == 2) &&
                    (move.getY() % 3 == 0 || move.getY() % 3 == 2))
                score += 30;

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private int countTwoInRow(IGameState state, IMove move, String player) {

        String[][] board = copyBoard(state);
        board[move.getX()][move.getY()] = player;

        int startX = move.getX() - (move.getX() % 3);
        int startY = move.getY() - (move.getY() % 3);

        int count = 0;

        for (int i = 0; i < 3; i++) {
            if (lineCount(board[startX+i][startY],
                    board[startX+i][startY+1],
                    board[startX+i][startY+2], player))
                count++;

            if (lineCount(board[startX][startY+i],
                    board[startX+1][startY+i],
                    board[startX+2][startY+i], player))
                count++;
        }

        if (lineCount(board[startX][startY],
                board[startX+1][startY+1],
                board[startX+2][startY+2], player))
            count++;

        if (lineCount(board[startX][startY+2],
                board[startX+1][startY+1],
                board[startX+2][startY], player))
            count++;

        return count;
    }

    private boolean lineCount(String a, String b, String c, String player) {

        int playerCount = 0;
        int emptyCount = 0;

        if (a.equals(player)) playerCount++; else if (a.equals(".")) emptyCount++;
        if (b.equals(player)) playerCount++; else if (b.equals(".")) emptyCount++;
        if (c.equals(player)) playerCount++; else if (c.equals(".")) emptyCount++;

        return playerCount == 2 && emptyCount == 1;
    }

    private boolean isWinningMove(IGameState state, IMove move, String player){

        String[][] board = copyBoard(state);
        board[move.getX()][move.getY()] = player;

        int startX = move.getX() - (move.getX() % 3);
        int startY = move.getY() - (move.getY() % 3);

        for(int i=0;i<3;i++)
            if(board[startX+i][startY].equals(player) &&
                    board[startX+i][startY+1].equals(player) &&
                    board[startX+i][startY+2].equals(player))
                return true;

        for(int i=0;i<3;i++)
            if(board[startX][startY+i].equals(player) &&
                    board[startX+1][startY+i].equals(player) &&
                    board[startX+2][startY+i].equals(player))
                return true;

        if(board[startX][startY].equals(player) &&
                board[startX+1][startY+1].equals(player) &&
                board[startX+2][startY+2].equals(player))
            return true;

        if(board[startX][startY+2].equals(player) &&
                board[startX+1][startY+1].equals(player) &&
                board[startX+2][startY].equals(player))
            return true;

        return false;
    }

    private String[][] copyBoard(IGameState state){
        return Arrays.stream(state.getField().getBoard())
                .map(String[]::clone)
                .toArray(String[][]::new);
    }

    private String getMyPlayerId(IGameState state){
        return state.getMoveNumber() % 2 == 0 ? "0" : "1";
    }

    @Override
    public String getBotName() {
        return "BobBot";
    }
}
