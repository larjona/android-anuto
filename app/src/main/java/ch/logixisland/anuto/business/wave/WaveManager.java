package ch.logixisland.anuto.business.wave;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.logixisland.anuto.business.game.GameState;
import ch.logixisland.anuto.business.game.GameStateListener;
import ch.logixisland.anuto.business.score.ScoreBoard;
import ch.logixisland.anuto.business.tower.TowerAging;
import ch.logixisland.anuto.data.game.ActiveWaveDescriptor;
import ch.logixisland.anuto.data.game.GameDescriptorRoot;
import ch.logixisland.anuto.data.setting.GameSettingsRoot;
import ch.logixisland.anuto.data.wave.WaveDescriptor;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.EntityRegistry;
import ch.logixisland.anuto.engine.logic.loop.Message;
import ch.logixisland.anuto.engine.logic.persistence.Persister;

public class WaveManager implements GameStateListener, Persister {

    private static final String TAG = WaveManager.class.getSimpleName();

    private static final int MAX_WAVES_IN_GAME = 3;
    private static final float MIN_WAVE_DELAY = 5;

    private final GameEngine mGameEngine;
    private final ScoreBoard mScoreBoard;
    private final GameState mGameState;
    private final TowerAging mTowerAging;
    private final EntityRegistry mEntityRegistry;

    private final EnemyDefaultHealth mEnemyDefaultHealth;

    private int mWaveNumber;
    private int mRemainingEnemiesCount;
    private boolean mNextWaveReady;
    private boolean mMinWaveDelayTimeout;

    private final List<WaveAttender> mActiveWaves = new ArrayList<>();
    private final List<WaveListener> mListeners = new CopyOnWriteArrayList<>();

    public WaveManager(GameEngine gameEngine, ScoreBoard scoreBoard, GameState gameState,
                       EntityRegistry entityRegistry, TowerAging towerAging) {
        mGameEngine = gameEngine;
        mScoreBoard = scoreBoard;
        mGameState = gameState;
        mTowerAging = towerAging;
        mEntityRegistry = entityRegistry;

        mEnemyDefaultHealth = new EnemyDefaultHealth(entityRegistry);

        gameState.addListener(this);
    }

    public int getWaveNumber() {
        return mWaveNumber;
    }

    public boolean isNextWaveReady() {
        return mNextWaveReady;
    }

    public int getRemainingEnemiesCount() {
        return mRemainingEnemiesCount;
    }

    public void startNextWave() {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(new Message() {
                @Override
                public void execute() {
                    startNextWave();
                }
            });
            return;
        }

        if (!mNextWaveReady) {
            return;
        }

        mGameState.setGameStarted();

        giveWaveRewardAndEarlyBonus();
        createAndStartWaveAttender();
        updateBonusOnScoreBoard();
        updateRemainingEnemiesCount();

        incrementNextWaveIndex();
        setNextWaveReady(false);
        triggerMinWaveDelay();
    }

    public void addListener(WaveListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(WaveListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void gameRestart() {
        mActiveWaves.clear();

        resetNextWaveIndex();
        setNextWaveReady(true);
        updateRemainingEnemiesCount();
    }

    @Override
    public void gameOver() {

    }

    @Override
    public void writeDescriptor(GameDescriptorRoot gameDescriptor) {
        gameDescriptor.setWaveNumber(mWaveNumber);

        for (WaveAttender waveAttender : mActiveWaves) {
            ActiveWaveDescriptor activeWaveDescriptor = new ActiveWaveDescriptor();
            activeWaveDescriptor.setWaveNumber(waveAttender.getWaveNumber());
            activeWaveDescriptor.setWaveStartTickCount(waveAttender.getWaveStartTickCount());
            activeWaveDescriptor.setExtend(waveAttender.getExtend());
            activeWaveDescriptor.setWaveReward(waveAttender.getWaveReward());
            activeWaveDescriptor.setEnemyHealthModifier(waveAttender.getEnemyHealthModifier());
            activeWaveDescriptor.setEnemyRewardModifier(waveAttender.getEnemyRewardModifier());
            gameDescriptor.addActiveWaveDescriptor(activeWaveDescriptor);
        }
    }

    @Override
    public void readDescriptor(GameDescriptorRoot gameDescriptor) {
        int lastStartedWaveTickCount = 0;
        List<WaveDescriptor> waveDescriptors = mGameEngine.getGameConfiguration().getWaveDescriptorRoot().getWaves();
        mWaveNumber = gameDescriptor.getWaveNumber();

        for (ActiveWaveDescriptor activeWaveDescriptor : gameDescriptor.getActiveWaveDescriptors()) {
            WaveDescriptor waveDescriptor = waveDescriptors.get(activeWaveDescriptor.getWaveNumber() % waveDescriptors.size());
            WaveAttender waveAttender = new WaveAttender(mGameEngine, mScoreBoard, mEntityRegistry, this, waveDescriptor, activeWaveDescriptor.getWaveNumber());
            waveAttender.setExtend(activeWaveDescriptor.getExtend());
            waveAttender.setWaveReward(activeWaveDescriptor.getWaveReward());
            waveAttender.modifyEnemyHealth(waveAttender.getEnemyHealthModifier());
            waveAttender.modifyEnemyReward(waveAttender.getEnemyRewardModifier());
            waveAttender.start(activeWaveDescriptor.getWaveStartTickCount());
            mActiveWaves.add(waveAttender);

            lastStartedWaveTickCount = Math.max(lastStartedWaveTickCount, activeWaveDescriptor.getWaveStartTickCount());
        }

        int nextWaveReadyTicks = Math.round(MIN_WAVE_DELAY * GameEngine.TARGET_FRAME_RATE) - (mGameEngine.getTickCount() - lastStartedWaveTickCount);

        if (nextWaveReadyTicks > 0) {
            setNextWaveReady(false);
            mMinWaveDelayTimeout = false;

            mGameEngine.postAfterTicks(new Message() {
                @Override
                public void execute() {
                    mMinWaveDelayTimeout = true;
                    updateNextWaveReady();
                }
            }, nextWaveReadyTicks);
        }
    }

    void enemyRemoved() {
        updateBonusOnScoreBoard();
        updateRemainingEnemiesCount();
    }

    void waveFinished(WaveAttender waveAttender) {
        mActiveWaves.remove(waveAttender);

        mTowerAging.ageTowers();
        updateBonusOnScoreBoard();
        updateNextWaveReady();
    }

    private void giveWaveRewardAndEarlyBonus() {
        if (!mActiveWaves.isEmpty()) {
            getCurrentWave().giveWaveReward();
            mScoreBoard.giveCredits(getEarlyBonus(), false);
        }
    }

    private void triggerMinWaveDelay() {
        mMinWaveDelayTimeout = false;

        mGameEngine.postDelayed(new Message() {
            @Override
            public void execute() {
                mMinWaveDelayTimeout = true;
                updateNextWaveReady();
            }
        }, MIN_WAVE_DELAY);
    }

    private void updateNextWaveReady() {
        if (mNextWaveReady) {
            return;
        }

        if (!mMinWaveDelayTimeout) {
            return;
        }

        if (mActiveWaves.size() >= MAX_WAVES_IN_GAME) {
            return;
        }

        setNextWaveReady(true);
    }

    private void updateBonusOnScoreBoard() {
        mScoreBoard.setEarlyBonus(getEarlyBonus());

        if (!mActiveWaves.isEmpty()) {
            mScoreBoard.setWaveBonus(getCurrentWave().getWaveReward());
        } else {
            mScoreBoard.setWaveBonus(0);
        }
    }

    private void updateRemainingEnemiesCount() {
        int totalCount = 0;

        for (WaveAttender waveAttender : mActiveWaves) {
            totalCount += waveAttender.getRemainingEnemiesCount();
        }

        if (mRemainingEnemiesCount != totalCount) {
            mRemainingEnemiesCount = totalCount;

            for (WaveListener listener : mListeners) {
                listener.remainingEnemiesCountChanged();
            }
        }
    }

    private void createAndStartWaveAttender() {
        List<WaveDescriptor> waveDescriptors = mGameEngine.getGameConfiguration().getWaveDescriptorRoot().getWaves();
        WaveDescriptor nextWaveDescriptor = waveDescriptors.get(mWaveNumber % waveDescriptors.size());
        WaveAttender nextWave = new WaveAttender(mGameEngine, mScoreBoard, mEntityRegistry, this, nextWaveDescriptor, mWaveNumber);
        updateWaveExtend(nextWave, nextWaveDescriptor);
        updateWaveModifiers(nextWave);
        nextWave.start();
        mActiveWaves.add(nextWave);
    }

    private void updateWaveExtend(WaveAttender wave, WaveDescriptor waveDescriptor) {
        int extend = Math.min((getIterationNumber() - 1) * waveDescriptor.getExtend(), waveDescriptor.getMaxExtend());
        wave.setExtend(extend);
    }

    private void updateWaveModifiers(WaveAttender wave) {
        GameSettingsRoot settings = mGameEngine.getGameConfiguration().getGameSettingsRoot();

        float waveHealth = wave.getWaveDefaultHealth(this.mEnemyDefaultHealth);
        float damagePossible = settings.getDifficultyLinear() * mScoreBoard.getCreditsEarned()
                + settings.getDifficultyModifier() * (float) Math.pow(mScoreBoard.getCreditsEarned(), settings.getDifficultyExponent());
        float healthModifier = damagePossible / waveHealth;
        healthModifier = Math.max(healthModifier, settings.getMinHealthModifier());

        float rewardModifier = settings.getRewardModifier() * (float) Math.pow(healthModifier, settings.getRewardExponent());
        rewardModifier = Math.max(rewardModifier, settings.getMinRewardModifier());

        wave.modifyEnemyHealth(healthModifier);
        wave.modifyEnemyReward(rewardModifier);
        wave.modifyWaveReward(getIterationNumber());

        Log.i(TAG, String.format("waveNumber=%d", getWaveNumber()));
        Log.i(TAG, String.format("waveHealth=%f", waveHealth));
        Log.i(TAG, String.format("creditsEarned=%d", mScoreBoard.getCreditsEarned()));
        Log.i(TAG, String.format("damagePossible=%f", damagePossible));
        Log.i(TAG, String.format("healthModifier=%f", healthModifier));
        Log.i(TAG, String.format("rewardModifier=%f", rewardModifier));
    }

    private int getIterationNumber() {
        return (getWaveNumber() / mGameEngine.getGameConfiguration().getWaveDescriptorRoot().getWaves().size()) + 1;
    }

    private int getEarlyBonus() {
        float remainingReward = 0;

        for (WaveAttender wave : mActiveWaves) {
            remainingReward += wave.getRemainingEnemiesReward();
        }

        GameSettingsRoot settings = mGameEngine.getGameConfiguration().getGameSettingsRoot();
        return Math.round(settings.getEarlyModifier() * (float) Math.pow(remainingReward, settings.getEarlyExponent()));
    }

    private WaveAttender getCurrentWave() {
        if (mActiveWaves.isEmpty()) {
            return null;
        }

        return mActiveWaves.get(mActiveWaves.size() - 1);
    }

    private void resetNextWaveIndex() {
        if (mWaveNumber != 0) {
            mWaveNumber = 0;

            for (WaveListener listener : mListeners) {
                listener.waveNumberChanged();
            }
        }
    }

    private void incrementNextWaveIndex() {
        mWaveNumber++;

        for (WaveListener listener : mListeners) {
            listener.waveNumberChanged();
        }
    }

    private void setNextWaveReady(boolean ready) {
        if (mNextWaveReady != ready) {
            mNextWaveReady = ready;

            for (WaveListener listener : mListeners) {
                listener.nextWaveReadyChanged();
            }
        }
    }
}
