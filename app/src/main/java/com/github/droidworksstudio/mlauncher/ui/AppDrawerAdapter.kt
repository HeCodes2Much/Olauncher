/**
 * Prepare the data for the app drawer, which is the list of all the installed applications.
 */

package com.github.droidworksstudio.mlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity.LEFT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.github.droidworksstudio.common.isSystemApp
import com.github.droidworksstudio.common.showKeyboard
import com.github.droidworksstudio.fuzzywuzzy.FuzzyFinder
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.AppListItem
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Constants.AppDrawerFlag
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.databinding.AdapterAppDrawerBinding
import com.github.droidworksstudio.mlauncher.helper.dp2px
import com.github.droidworksstudio.mlauncher.helper.utils.PrivateSpaceManager

class AppDrawerAdapter(
    private val context: Context,
    private var flag: AppDrawerFlag,
    private val gravity: Int,
    private val appClickListener: (AppListItem) -> Unit,
    private val appDeleteListener: (AppListItem) -> Unit,
    private val appRenameListener: (String, String) -> Unit,
    private val appHideListener: (AppDrawerFlag, AppListItem) -> Unit,
    private val appInfoListener: (AppListItem) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    private lateinit var prefs: Prefs
    private var appFilter = createAppFilter()
    var appsList: MutableList<AppListItem> = mutableListOf()
    var appFilteredList: MutableList<AppListItem> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding

    private var isBangSearch = false


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        binding =
            AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefs = Prefs(parent.context)
        val fontColor = prefs.appColor
        binding.appTitle.setTextColor(fontColor)

        binding.appTitle.textSize = prefs.appSize.toFloat()
        val padding: Int = prefs.textPaddingSize
        binding.appTitle.setPadding(0, padding, 0, padding)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (appFilteredList.isEmpty()) return
        val appModel = appFilteredList[holder.absoluteAdapterPosition]
        holder.bind(flag, gravity, appModel, appClickListener, appInfoListener, appDeleteListener)

        holder.appHide.setOnClickListener {
            appFilteredList.removeAt(holder.absoluteAdapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.absoluteAdapterPosition)
            appHideListener(flag, appModel)
        }

        holder.appLock.setOnClickListener {
            val appName = appModel.activityPackage
            // Access the current locked apps set
            val currentLockedApps = prefs.lockedApps

            if (currentLockedApps.contains(appName)) {
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.padlock_off,
                    0,
                    0
                )
                holder.appLock.text = context.getString(R.string.lock)
                // If appName is already in the set, remove it
                currentLockedApps.remove(appName)
            } else {
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                holder.appLock.text = context.getString(R.string.unlock)
                // If appName is not in the set, add it
                currentLockedApps.add(appName)
            }

            // Update the lockedApps value (save the updated set back to prefs)
            prefs.lockedApps = currentLockedApps
            Log.d("lockedApps", prefs.lockedApps.toString())
        }

        holder.appSaveRename.setOnClickListener {
            val name = holder.appRenameEdit.text.toString().trim()
            appModel.customLabel = name
            notifyItemChanged(holder.absoluteAdapterPosition)
            appRenameListener(appModel.activityPackage, appModel.customLabel)
        }

        autoLaunch(position)
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") == true
                prefs = Prefs(context)

                val searchChars = charSearch.toString()
                val filteredApps: MutableList<AppListItem>

                if (prefs.filterStrength >= 1) {
                    val scoredApps = mutableMapOf<AppListItem, Int>()
                    for (app in appsList) {
                        scoredApps[app] =
                            FuzzyFinder.scoreApp(app, searchChars, Constants.MAX_FILTER_STRENGTH)
                    }

                    filteredApps = if (searchChars.isNotEmpty()) {
                        if (prefs.searchFromStart) {
                            scoredApps.filter { (app, _) ->
                                app.label.startsWith(
                                    searchChars,
                                    ignoreCase = true
                                )
                            }
                                .filter { (_, score) -> score > prefs.filterStrength }
                                .map { it.key }
                                .toMutableList()
                        } else {
                            scoredApps.filterValues { it > prefs.filterStrength }
                                .keys
                                .toMutableList()
                        }
                    } else {
                        appsList.toMutableList()
                    }
                } else {
                    filteredApps = (if (searchChars.isEmpty()) appsList
                    else appsList.filter { app ->
                        FuzzyFinder.normalizeString(app.label, searchChars)
                    } as MutableList<AppListItem>)
                }

                val filterResults = FilterResults()
                filterResults.values = filteredApps
                return filterResults
            }


            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values is MutableList<*>) {
                    appFilteredList = results.values as MutableList<AppListItem>
                    notifyDataSetChanged()
                } else {
                    return
                }
            }
        }
    }

    private fun autoLaunch(position: Int) {
        val lastMatch = itemCount == 1
        val openApp = flag == AppDrawerFlag.LaunchApp
        val autoOpenApp = prefs.autoOpenApp
        if (lastMatch && openApp && autoOpenApp) {
            try { // Automatically open the app when there's only one search result
                if (isBangSearch.not())
                    appClickListener(appFilteredList[position])
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setAppList(appsList: MutableList<AppListItem>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyDataSetChanged()
    }

    fun launchFirstInList() {
        if (appFilteredList.isNotEmpty())
            appClickListener(appFilteredList[0])
    }

    class ViewHolder(itemView: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(itemView.root) {
        val appHide: TextView = itemView.appHide
        val appLock: TextView = itemView.appLock
        val appRenameEdit: EditText = itemView.appRenameEdit
        val appSaveRename: TextView = itemView.appSaveRename

        private val appHideLayout: LinearLayout = itemView.appHideLayout
        private val appRenameLayout: LinearLayout = itemView.appRenameLayout
        private val appRename: TextView = itemView.appRename
        private val appTitle: TextView = itemView.appTitle
        private val appTitleFrame: FrameLayout = itemView.appTitleFrame
        private val appClose: TextView = itemView.appClose
        private val appInfo: TextView = itemView.appInfo
        private val appDelete: TextView = itemView.appDelete

        @SuppressLint("RtlHardcoded", "NewApi")
        fun bind(
            flag: AppDrawerFlag,
            appLabelGravity: Int,
            appListItem: AppListItem,
            appClickListener: (AppListItem) -> Unit,
            appInfoListener: (AppListItem) -> Unit,
            appDeleteListener: (AppListItem) -> Unit
        ) =
            with(itemView) {
                val prefs = Prefs(context)
                appHideLayout.visibility = View.GONE
                appRenameLayout.visibility = View.GONE

                // set show/hide icon
                if (flag == AppDrawerFlag.HiddenApps) {
                    appHide.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.visibility, 0, 0)
                    appHide.text = context.getString(R.string.show)
                } else {
                    appHide.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        R.drawable.visibility_off,
                        0,
                        0
                    )
                    appHide.text = context.getString(R.string.hide)
                }

                val appName = appListItem.activityPackage
                // Access the current locked apps set
                val currentLockedApps = prefs.lockedApps

                if (currentLockedApps.contains(appName)) {
                    appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                    appLock.text = context.getString(R.string.unlock)
                } else {
                    appLock.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        R.drawable.padlock_off,
                        0,
                        0
                    )
                    appLock.text = context.getString(R.string.lock)

                }

                appRename.apply {
                    setOnClickListener {
                        if (appListItem.activityPackage.isNotEmpty()) {
                            appRenameEdit.hint = appListItem.activityLabel
                            appRenameLayout.visibility = View.VISIBLE
                            appHideLayout.visibility = View.GONE
                            appRenameEdit.showKeyboard()
                            appRenameEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                        }
                    }
                }

                appRenameEdit.apply {
                    addTextChangedListener(object : TextWatcher {

                        override fun afterTextChanged(s: Editable) {}

                        override fun beforeTextChanged(
                            s: CharSequence, start: Int,
                            count: Int, after: Int
                        ) {
                        }

                        override fun onTextChanged(
                            s: CharSequence, start: Int,
                            before: Int, count: Int
                        ) {
                            if (appRenameEdit.text.isEmpty()) {
                                appSaveRename.text = context.getString(R.string.reset)
                            } else if (appRenameEdit.text.toString() == appListItem.customLabel) {
                                appSaveRename.text = context.getString(R.string.cancel)
                            } else {
                                appSaveRename.text = context.getString(R.string.rename)
                            }
                        }
                    })
                    // set current name as default text in EditText
                    text = Editable.Factory.getInstance().newEditable(appListItem.label)
                }

                appTitle.text = appListItem.label

                // set text gravity
                val params = appTitle.layoutParams as FrameLayout.LayoutParams
                params.gravity = appLabelGravity
                appTitle.layoutParams = params

                // add icon next to app name to indicate that this app is installed on another profile
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val isPrivateSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    launcherApps.getLauncherUserInfo(appListItem.user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
                } else {
                    PrivateSpaceManager(context).isPrivateSpaceSupported()
                }
                val isWorkProfile = appListItem.user != android.os.Process.myUserHandle() && !isPrivateSpace

                if (isWorkProfile) {
                    val icon = AppCompatResources.getDrawable(context, R.drawable.work_profile)
                    val px = dp2px(resources, prefs.appSize)
                    icon?.setBounds(0, 0, px, px)
                    if (appLabelGravity == LEFT) {
                        appTitle.setCompoundDrawables(null, null, icon, null)
                    } else {
                        appTitle.setCompoundDrawables(icon, null, null, null)
                    }
                    appTitle.compoundDrawablePadding = 20
                } else if (isPrivateSpace) {
                    val icon = AppCompatResources.getDrawable(context, R.drawable.ic_unlock)
                    val px = dp2px(resources, prefs.appSize)
                    icon?.setBounds(0, 0, px, px)
                    if (appLabelGravity == LEFT) {
                        appTitle.setCompoundDrawables(null, null, icon, null)
                    } else {
                        appTitle.setCompoundDrawables(icon, null, null, null)
                    }
                    appTitle.compoundDrawablePadding = 20
                } else {
                    appTitle.setCompoundDrawables(null, null, null, null)
                }

                val padding = dp2px(resources, 24)
                appTitle.updatePadding(left = padding, right = padding)

                appTitleFrame.apply {
                    setOnClickListener {
                        appClickListener(appListItem)
                    }
                    setOnLongClickListener {
                        val openApp =
                            flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps
                        if (openApp) {
                            try {
                                appDelete.alpha =
                                    if (context.isSystemApp(appListItem.activityPackage)) 0.3f else 1.0f
                                appHideLayout.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        true
                    }
                }

                appInfo.apply {
                    setOnClickListener {
                        appInfoListener(appListItem)
                    }
                }

                appDelete.apply {
                    setOnClickListener {
                        appDeleteListener(appListItem)
                    }
                }

                appClose.apply {
                    setOnClickListener {
                        appHideLayout.visibility = View.GONE
                    }
                }
            }
    }
}
