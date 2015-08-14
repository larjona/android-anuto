package ch.bfh.anuto.game.objects.impl;

import android.graphics.Canvas;

import ch.bfh.anuto.R;
import ch.bfh.anuto.game.Layers;
import ch.bfh.anuto.game.objects.Enemy;
import ch.bfh.anuto.game.objects.Shot;
import ch.bfh.anuto.game.objects.Sprite;
import ch.bfh.anuto.util.iterator.StreamIterator;
import ch.bfh.anuto.util.math.Vector2;

public class CanonShotMG extends Shot {

    private final static float DAMAGE = 60f;
    private final static float MOVEMENT_SPEED = 8.0f;
    private final static float SHOT_WIDTH = 1f;

    private float mAngle;

    private Sprite mSprite;

    public CanonShotMG(Vector2 position, Vector2 direction) {
        mSpeed = MOVEMENT_SPEED;
        mDirection = direction;
        mAngle = mDirection.angle();

        setPosition(position);
    }

    @Override
    public void onInit() {
        super.onInit();

        mSprite = Sprite.fromResources(mGame.getResources(), R.drawable.canon_mg_shot, 4);
        mSprite.setListener(this);
        mSprite.setIndex(mGame.getRandom().nextInt(4));
        mSprite.setMatrix(0.2f, null, null, -90f);
        mSprite.setLayer(Layers.SHOT);
        mGame.add(mSprite);
    }

    @Override
    public void onClean() {
        super.onClean();

        mGame.remove(mSprite);
    }

    @Override
    public void onDraw(Sprite sprite, Canvas canvas) {
        super.onDraw(sprite, canvas);

        canvas.rotate(mAngle);
    }

    @Override
    public void onTick() {
        super.onTick();

        if (!mGame.inGame(mPosition)) {
            this.remove();
            return;
        }

        StreamIterator<Enemy> encountered = getEncounteredEnemies(SHOT_WIDTH);
        if (encountered.hasNext()) {
            Enemy enemy = encountered.next();
            encountered.close();

            enemy.damage(DAMAGE);
            this.remove();
            return;
        }
    }
}
