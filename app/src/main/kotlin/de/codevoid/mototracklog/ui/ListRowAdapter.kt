package de.codevoid.mototracklog.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.codevoid.mototracklog.databinding.ItemListRowBinding

data class ListRowItem(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Unit)? = null,
)

/** A vertical list of rows: tap runs [ListRowItem.onClick], a touch long press its menu. */
class ListRowAdapter : RecyclerView.Adapter<ListRowAdapter.RowViewHolder>() {

    private var items: List<ListRowItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<ListRowItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class RowViewHolder(val binding: ItemListRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemListRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.blockKeyLongPress()
        return RowViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.binding.bind(items[position])
    }
}

/**
 * Fills a list row. A recycled holder carries the previous item's subtitle and long-press state,
 * so both are always reset; a row without a menu must not keep arming the long-press timer.
 */
fun ItemListRowBinding.bind(item: ListRowItem) {
    rowTitle.text = item.title
    rowSubtitle.text = item.subtitle
    rowSubtitle.visibility = if (item.subtitle != null) View.VISIBLE else View.GONE
    root.setOnClickListener { item.onClick() }
    val onLongClick = item.onLongClick
    if (onLongClick != null) {
        root.setOnLongClickListener { onLongClick(); true }
    } else {
        root.setOnLongClickListener(null)
        root.isLongClickable = false
    }
}
