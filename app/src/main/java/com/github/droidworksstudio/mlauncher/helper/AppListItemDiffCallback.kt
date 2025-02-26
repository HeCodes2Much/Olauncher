package com.github.droidworksstudio.mlauncher.helper

import androidx.recyclerview.widget.DiffUtil
import com.github.droidworksstudio.mlauncher.data.AppListItem

// DiffUtil for efficiently handling list updates
class AppListItemDiffCallback(
    private val oldList: List<AppListItem>,
    private val newList: List<AppListItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].activityPackage == newList[newItemPosition].activityPackage
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}