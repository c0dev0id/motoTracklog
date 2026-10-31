package de.codevoid.mototracklog.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.codevoid.mototracklog.databinding.ItemTileBinding

data class TileItem(
    val label: String,
    val subtitle: String? = null,
    val onClick: () -> Unit = {},
)

/** A grid of text tiles; the settings mode's adapter. */
class TileAdapter(private var items: List<TileItem>) :
    RecyclerView.Adapter<TileAdapter.TileViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<TileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Replaces the first item that [matches] with [update] of it; does nothing when none matches. */
    fun updateItem(matches: (TileItem) -> Boolean, update: (TileItem) -> TileItem) {
        val position = items.indexOfFirst(matches)
        if (position == -1) return
        items = items.toMutableList().also { it[position] = update(it[position]) }
        notifyItemChanged(position)
    }

    class TileViewHolder(val binding: ItemTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        return TileViewHolder(ItemTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val item = items[position]
        holder.binding.bind(item.label, item.subtitle)
        holder.binding.root.setOnClickListener { item.onClick() }
    }
}

/** Fills a text tile. A recycled holder carries the previous item's subtitle, so it is always reset. */
fun ItemTileBinding.bind(label: String, subtitle: String?) {
    tileLabel.text = label
    tileSubtitle.text = subtitle
    tileSubtitle.visibility = if (subtitle != null) View.VISIBLE else View.GONE
}
