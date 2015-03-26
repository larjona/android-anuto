package ch.bfh.anuto;

import android.app.Activity;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.List;

import ch.bfh.anuto.game.objects.impl.BasicTower;

public class InventoryFragment extends Fragment {

    public interface Listener {
        public void onNextWaveClick();
    }

    private Listener mListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inventory, container, false);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mListener = (Listener)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement TowerInfoFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mListener = null;
    }

    public void onNextWaveClick(View view) {
        mListener.onNextWaveClick();
    }
}
