package ch.bfh.anuto.game.objects.impl;

import android.graphics.Canvas;

import ch.bfh.anuto.R;
import ch.bfh.anuto.game.GameEngine;
import ch.bfh.anuto.game.Layers;
import ch.bfh.anuto.game.objects.AimingTower;
import ch.bfh.anuto.game.objects.Shot;
import ch.bfh.anuto.game.objects.Sprite;
import ch.bfh.anuto.util.math.SineFunction;
import ch.bfh.anuto.util.math.Vector2;

public class Canon extends AimingTower {

    private final static int VALUE = 200;
    private final static float RELOAD_TIME = 0.4f;
    private final static float RANGE = 3.5f;
    private final static float SHOT_SPAWN_OFFSET = 0.9f;

    private final static float REBOUND_RANGE = 0.25f;
    private final static float REBOUND_DURATION = 0.2f;

    private float mAngle;

    private boolean mReboundActive;
    private SineFunction mReboundFunction;

    private Sprite mSpriteBase;
    private Sprite mSpriteCanon;

    public Canon() {
        mValue = VALUE;
        mRange = RANGE;
        mReloadTime = RELOAD_TIME;
    }

    @Override
    public void onInit() {
        super.onInit();

        mAngle = mGame.getRandom(360f);
        mReboundActive = false;

        mReboundFunction = new SineFunction();
        mReboundFunction.setProperties(0f, (float)Math.PI, REBOUND_RANGE, 0f);
        mReboundFunction.setSection(GameEngine.TARGET_FRAME_RATE * REBOUND_DURATION);

        mSpriteBase = Sprite.fromResources(mGame.getResources(), R.drawable.base1, 4);
        mSpriteBase.setListener(this);
        mSpriteBase.setIndex(mGame.getRandom().nextInt(4));
        mSpriteBase.setMatrix(1f, 1f, null, null);
        mSpriteBase.setLayer(Layers.TOWER_BASE);
        mGame.add(mSpriteBase);

        mSpriteCanon = Sprite.fromResources(mGame.getResources(), R.drawable.canon, 4);
        mSpriteCanon.setListener(this);
        mSpriteCanon.setIndex(mGame.getRandom().nextInt(4));
        mSpriteCanon.setMatrix(0.3f, 1.0f, new Vector2(0.15f, 0.2f), -90f);
        mSpriteCanon.setLayer(Layers.TOWER);
        mGame.add(mSpriteCanon);
    }

    @Override
    public void onClean() {
        super.onClean();

        mGame.remove(mSpriteBase);
        mGame.remove(mSpriteCanon);
    }

    @Override
    public void onDraw(Sprite sprite, Canvas canvas) {
        super.onDraw(sprite, canvas);

        canvas.rotate(mAngle);

        if (sprite == mSpriteCanon && mReboundActive) {
            canvas.translate(-mReboundFunction.getValue(), 0);
        }
    }

    @Override
    public void onTick() {
        super.onTick();

        if (mTarget != null) {
            mAngle = getAngleTo(mTarget);

            if (mReloaded) {
                Shot shot = new CanonShot(mPosition, mTarget);
                shot.move(Vector2.createPolar(SHOT_SPAWN_OFFSET, mAngle));
                shoot(shot);

                mReboundActive = true;
            }
        }

        if (mReboundActive) {
            if (mReboundFunction.step()) {
                mReboundFunction.reset();
                mReboundActive = false;
            }
        }
    }
}
