package com.example.freeavbrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class FavoritesActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var searchBarContainer: MaterialCardView
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var btnMenu: MaterialButton
    private lateinit var fabActions: ExtendedFloatingActionButton
    private lateinit var adapter: FavoritesAdapter
    private lateinit var favoritesManager: FavoritesManager
    private var allFavorites: List<FavoriteItem> = emptyList()
    private var isSearchVisible = false

    // Activity Result Launchers
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val success = favoritesManager.exportFavoritesToFile(this, it)
            val msg = if (success) getString(R.string.export_success) else getString(R.string.export_failed)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val result = favoritesManager.importFavoritesFromFile(this, it)
            Toast.makeText(this, result.second, Toast.LENGTH_SHORT).show()
            if (result.first) {
                loadFavorites()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_favorites)

        favoritesManager = FavoritesManager(this)
        initViews()
        setupRecyclerView()
        setupListeners()
        loadFavorites()

        // Enter transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.rv_favorites)
        emptyState = findViewById(R.id.empty_state)
        searchBarContainer = findViewById(R.id.search_bar_container)
        etSearch = findViewById(R.id.et_search)
        btnSearch = findViewById(R.id.btn_search)
        btnMenu = findViewById(R.id.btn_menu)
        fabActions = findViewById(R.id.fab_actions)

        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        // Grid layout with 2 columns
        val spanCount = 2
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        adapter = FavoritesAdapter(
            onItemClick = { item ->
                val intent = Intent()
                intent.putExtra("url", item.url)
                setResult(RESULT_OK, intent)
                finish()
            },
            onDeleteClick = { item ->
                showDeleteConfirmDialog(item)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        // Search button
        btnSearch.setOnClickListener {
            toggleSearchBar()
        }

        // Menu button - show import/export options
        btnMenu.setOnClickListener {
            showMenuOptions()
        }

        // FAB actions
        fabActions.setOnClickListener {
            showMenuOptions()
        }

        // Search text listener
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFavorites(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Empty state button
        findViewById<MaterialButton>(R.id.btn_empty_browse).setOnClickListener {
            finish()
        }
    }

    private fun toggleSearchBar() {
        isSearchVisible = !isSearchVisible
        searchBarContainer.visibility = if (isSearchVisible) View.VISIBLE else View.GONE

        if (isSearchVisible) {
            etSearch.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            etSearch.setText("")
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        }
    }

    private fun showMenuOptions() {
        val options = arrayOf(
            getString(R.string.import_favorites),
            getString(R.string.export_favorites)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.manage))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importLauncher.launch(arrayOf("application/json"))
                    1 -> exportLauncher.launch("javbrowser_favorites.json")
                }
            }
            .show()
    }

    private fun showDeleteConfirmDialog(item: FavoriteItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除收藏")
            .setMessage("确定要删除「${item.title}」吗？")
            .setPositiveButton("删除") { _, _ ->
                favoritesManager.removeFavorite(item.url)
                loadFavorites()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun filterFavorites(query: String) {
        val filtered = if (query.isEmpty()) {
            allFavorites
        } else {
            allFavorites.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun loadFavorites() {
        allFavorites = favoritesManager.getFavorites()
        filterFavorites(etSearch.text.toString())
    }

    // DiffUtil callback
    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteItem>() {
        override fun areItemsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
            return oldItem == newItem
        }
    }

    inner class FavoritesAdapter(
        private val onItemClick: (FavoriteItem) -> Unit,
        private val onDeleteClick: (FavoriteItem) -> Unit
    ) : ListAdapter<FavoriteItem, FavoritesAdapter.ViewHolder>(FavoriteDiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardFavorite: MaterialCardView = view.findViewById(R.id.card_favorite)
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvUrl: TextView = view.findViewById(R.id.tv_url)
            val ivThumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
            val ivPlaceholder: ImageView = view.findViewById(R.id.iv_placeholder)
            val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_favorite, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)

            holder.tvTitle.text = item.title
            holder.tvUrl.text = extractDomain(item.url)

            // Load thumbnail
            if (!item.thumbnailUrl.isNullOrEmpty()) {
                holder.ivPlaceholder.visibility = View.GONE
                Glide.with(holder.itemView.context)
                    .load(android.util.Base64.decode(item.thumbnailUrl, android.util.Base64.DEFAULT))
                    .into(holder.ivThumbnail)
            } else {
                holder.ivPlaceholder.visibility = View.VISIBLE
            }

            // Click listeners
            holder.cardFavorite.setOnClickListener {
                onItemClick(item)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }

        private fun extractDomain(url: String): String {
            return try {
                val uri = android.net.Uri.parse(url)
                uri.host ?: url
            } catch (e: Exception) {
                url
            }
        }
    }
}
