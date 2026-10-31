package de.codevoid.mototracklog.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import de.codevoid.mototracklog.R
import de.codevoid.mototracklog.databinding.DialogOptionsBinding

/**
 * Pick one of a list of options, as tile-styled rows in the app's own dialog theme —
 * the same surface as the tile-action menus, not an `AlertDialog`.
 *
 * It exists because cycling a multi-valued setting by repeated tapping applies every value
 * on the way to the one you want. For Orientation that means the screen rotating through
 * up to four unwanted orientations to reach the fifth, which is unpleasant enough to be a
 * reason not to change the setting at all.
 *
 * Rows are inflated from `item_dialog_option` rather than constructed, so the style's
 * `layout_*` attributes are resolved by the parent the way the fixed rows' are. The
 * current value is drawn in the accent colour: with the tile hidden behind the dialog it
 * is otherwise not visible while choosing.
 *
 * Returns the shown dialog; tests use the handle.
 */
fun showOptionsDialog(
    context: Context,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
): Dialog {
    val inflater = LayoutInflater.from(context)
    val binding = DialogOptionsBinding.inflate(inflater)
    binding.optionsTitle.text = title

    val dialog = Dialog(context, R.style.Theme_MotoTracklog_Dialog)
    dialog.setContentView(binding.root)

    options.forEachIndexed { index, label ->
        val row = inflater.inflate(R.layout.item_dialog_option, binding.optionRows, false) as TextView
        row.text = label
        if (index == selectedIndex) {
            row.setTextColor(ContextCompat.getColor(context, R.color.tile_focused))
        }
        row.setOnClickListener {
            dialog.dismiss()
            onPick(index)
        }
        binding.optionRows.addView(row)
    }

    dialog.showImmersive()
    return dialog
}
