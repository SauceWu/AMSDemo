package me.sauce.asmdemo;

import android.view.View;
import android.widget.Toast;

/**
 * @author sauce
 * @since 2019/1/19
 */
public class ToastClickListener {
    public static void trackViewOnClick(View v) {
        Toast.makeText(v.getContext(), "hook 成功", Toast.LENGTH_SHORT).show();
    }
}
