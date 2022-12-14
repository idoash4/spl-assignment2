package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    protected Thread dealerThread;

    protected final BlockingQueue<Integer> setChecks;

    private volatile boolean reshuffleState;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setChecks = new ArrayBlockingQueue<>(1, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        reshuffleState = true;
        createAndRunPlayerThreads();
        while (!shouldFinish()) {
            placeCardsOnTable();
            reshuffleState = false;
            timerLoop();
            reshuffleState = true;
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     *
     */
    private void createAndRunPlayerThreads() {
        for (Player player:players) {
            Thread playerThread = new Thread(player, "player "+player.id);
            playerThread.start();
        }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        Integer playerId = setChecks.poll();
        if (playerId != null) {
            env.logger.log(Level.INFO, "Waiting for player " + playerId + " lock to check for set");
            synchronized (players[playerId]) {
                env.logger.log(Level.INFO, "Checking player " + playerId + " set");
                Integer[] slots = players[playerId].getSlotsWithTokens();
                int[] cards = new int[3];
                for (int i = 0; i < slots.length; i++) {
                    if (slots[i] != null) {
                        cards[i] = table.slotToCard[slots[i]];
                    } else {
                        players[playerId].notifyAll();
                        env.logger.log(Level.INFO, "Set of player " + playerId + " has less than 3 cards");
                        return;
                    }
                }
                if (env.util.testSet(cards)) {
                    env.logger.log(Level.INFO, "Set of player " + playerId + " is valid");
                    for (int slot:slots) {
                        table.removeCard(slot);
                    }
                    players[playerId].point();
                } else {
                    env.logger.log(Level.INFO, "Set of player " + playerId + " is invalid");
                    players[playerId].penalty();
                }
                players[playerId].notifyAll();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        int empty_slots = table.countEmptySlots();
        if (empty_slots > 0) {
            Collections.shuffle(deck);
            for (int i = 0; i < empty_slots && deck.size() >= 1; i++) {
                // Attempt to place the card from the top of the deck on the board
                if (table.placeCard(deck.get(0)) != -1) {
                    // If the card was placed on the board remove it from the deck
                    deck.remove(0);
                } else {
                    env.logger.log(Level.WARNING, "Dealer attempted to place a card on a full board");
                }
            }
            updateTimerDisplay(true);
            table.hints();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            env.logger.log(Level.INFO, "Dealer thread was interrupted");
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        if (env.config.turnTimeoutMillis > 0) {
            long time_remaining = reshuffleTime - System.currentTimeMillis();
            env.ui.setCountdown(time_remaining > 0 ? time_remaining : 0, time_remaining < env.config.turnTimeoutWarningMillis);
        }
    }

    public boolean isReshuffling() {
        return reshuffleState;
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        deck.addAll(table.removeAllCards());
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
