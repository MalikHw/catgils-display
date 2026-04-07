package com.malikhw.catgirlsdisplay

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    
    private lateinit var imgView: ImageView
    private lateinit var loadingBar: ProgressBar
    private lateinit var btnNext: ImageButton
    private lateinit var btnMetadata: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var btnStar: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAbout: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var bottomBtns: LinearLayout
    
    private lateinit var prefs: PrefsManager
    private var currentImg: NekoImage? = null
    private var isFullscreen = false
    private var autoNextJob: Job? = null
    
    private val STORAGE_PERM = 101
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = PrefsManager(this)
        
        imgView = findViewById(R.id.imageView)
        loadingBar = findViewById(R.id.progressBar)
        btnNext = findViewById(R.id.btnNext)
        btnMetadata = findViewById(R.id.btnMetadata)
        btnSave = findViewById(R.id.btnSave)
        btnStar = findViewById(R.id.btnStar)
        btnSettings = findViewById(R.id.btnSettings)
        btnAbout = findViewById(R.id.btnAbout)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnShare = findViewById(R.id.btnShare)
        bottomBtns = findViewById(R.id.bottomButtons)
        
        setupGestures()
        setupButtons()
        
        loadNext()
        startAutoNext()
    }
    
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // what the fuck android gestures
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100) {
                    if (diffX > 0) {
                        
                    } else {
                        loadNext()
                    }
                    return true
                }
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isFullscreen) {
                    loadNext()
                }
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isFullscreen) {
                    exitFullscreen()
                }
                return true
            }
        })
        
        imgView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }
    
    private fun setupButtons() {
        btnNext.setOnClickListener { loadNext() }
        
        btnMetadata.setOnClickListener {
            currentImg?.let { showMetadata(it) }
        }
        
        btnSave.setOnClickListener {
            checkPermAndSave()
        }
        
        btnStar.setOnClickListener {
            currentImg?.let { toggleStar(it) }
        }
        
        btnSettings.setOnClickListener {
            showSettings()
        }
        
        btnAbout.setOnClickListener {
            showAbout()
        }
        
        btnFullscreen.setOnClickListener {
            enterFullscreen()
        }
        
        btnShare.setOnClickListener {
            shareImage()
        }
    }
    
    private fun loadNext() {
        loadingBar.visibility = View.VISIBLE
        imgView.setImageDrawable(null)
        
        lifecycleScope.launch {
            try {
                val nsfwParam = when(prefs.nsfwMode) {
                    "yes" -> "true"
                    "no" -> "false"
                    else -> null
                }
                
                val tags = prefs.selectedTags
                val resp = if (tags.isNotEmpty()) {
                    val nsfwBool = when(prefs.nsfwMode) {
                        "yes" -> true
                        "no" -> false
                        else -> null
                    }
                    ApiClient.service.searchImages(SearchRequest(
                        nsfw = nsfwBool,
                        tags = tags,
                        limit = 1
                    ))
                } else {
                    ApiClient.service.getRandomImages(nsfw = nsfwParam, count = 1)
                }
                
                if (resp.images.isNotEmpty()) {
                    currentImg = resp.images[0]
                    loadImage(currentImg!!)
                    updateStarButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingBar.visibility = View.GONE
            }
        }
    }
    
    private fun loadImage(img: NekoImage) {
        val url = "https://nekos.moe/image/${img.id}"
        Glide.with(this)
            .load(url)
            .into(imgView)
    }
    
    private fun showMetadata(img: NekoImage) {
        val msg = buildString {
            append("ID: ${img.id}\n")
            img.artist?.let { append("Artist: $it\n") }
            append("NSFW: ${if(img.nsfw) "Yes" else "No"}\n")
            img.likes?.let { append("Likes: $it\n") }
            img.favorites?.let { append("Favorites: $it\n") }
            img.uploader?.let { append("Uploader: ${it.username}\n") }
            if (img.tags.isNotEmpty()) {
                append("\nTags:\n${img.tags.joinToString(", ")}")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Metadata")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun checkPermAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES), STORAGE_PERM)
            } else {
                saveToGallery()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERM)
            } else {
                saveToGallery()
            }
        }
    }
    
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == STORAGE_PERM && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            saveToGallery()
        }
    }
    
    private fun saveToGallery() {
        currentImg?.let { img ->
            val url = "https://nekos.moe/image/${img.id}"
            
            Glide.with(this)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bmp: Bitmap, trans: Transition<in Bitmap>?) {
                        val saved = MediaStore.Images.Media.insertImage(
                            contentResolver, bmp, "catgirl_${img.id}", "Catgirl"
                        )
                        if (saved != null) {
                            Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }
    
    private fun toggleStar(img: NekoImage) {
        if (prefs.isStarred(img.id)) {
            prefs.removeStarred(img.id)
            Toast.makeText(this, "Removed from starred", Toast.LENGTH_SHORT).show()
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "starred")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, "${img.id}.jpg")
            val url = "https://nekos.moe/image/${img.id}"
            
            Glide.with(this)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bmp: Bitmap, trans: Transition<in Bitmap>?) {
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        prefs.addStarred(SavedImage(img.id, file.absolutePath, System.currentTimeMillis()))
                        Toast.makeText(this@MainActivity, "Starred!", Toast.LENGTH_SHORT).show()
                        updateStarButton()
                    }
                    
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
        updateStarButton()
    }
    
    private fun updateStarButton() {
        currentImg?.let {
            if (prefs.isStarred(it.id)) {
                btnStar.setImageResource(android.R.drawable.star_big_on)
            } else {
                btnStar.setImageResource(android.R.drawable.star_big_off)
            }
        }
    }
    
    private fun showSettings() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.settings_sheet, null)
        
        val rgNsfw = view.findViewById<RadioGroup>(R.id.radioGroupNsfw)
        val etTags = view.findViewById<EditText>(R.id.editTextTags)
        val sliderInterval = view.findViewById<Slider>(R.id.sliderInterval)
        val tvInterval = view.findViewById<TextView>(R.id.tvIntervalValue)
        
        when(prefs.nsfwMode) {
            "everything" -> rgNsfw.check(R.id.radioEverything)
            "yes" -> rgNsfw.check(R.id.radioYes)
            "no" -> rgNsfw.check(R.id.radioNo)
        }
        
        etTags.setText(prefs.selectedTags.joinToString(", "))
        sliderInterval.value = prefs.autoNextInterval.toFloat()
        tvInterval.text = "${prefs.autoNextInterval}s"
        
        sliderInterval.addOnChangeListener { _, val_, _ ->
            tvInterval.text = "${val_.toInt()}s"
        }
        
        view.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            when(rgNsfw.checkedRadioButtonId) {
                R.id.radioEverything -> prefs.nsfwMode = "everything"
                R.id.radioYes -> prefs.nsfwMode = "yes"
                R.id.radioNo -> prefs.nsfwMode = "no"
            }
            
            val tagsText = etTags.text.toString()
            prefs.selectedTags = if (tagsText.isBlank()) {
                emptyList()
            } else {
                tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            
            prefs.autoNextInterval = sliderInterval.value.toInt()
            
            dialog.dismiss()
            startAutoNext()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }
    
    private fun showAbout() {
        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.about_dialog, null)
        
        view.findViewById<TextView>(R.id.btnYoutube).setOnClickListener {
            openUrl("https://youtube.com/@MalikHw47")
        }
        
        view.findViewById<TextView>(R.id.btnGithub).setOnClickListener {
            openUrl("https://github.com/MalikHw/catgirlsdisplay")
        }
        
        view.findViewById<TextView>(R.id.btnTiktok).setOnClickListener {
            openUrl("https://tiktok.com/@malikhw47")
        }
        
        view.findViewById<TextView>(R.id.btnDonate).setOnClickListener {
            openUrl("https://malikhw.github.io/donate")
        }
        
        dialog.setView(view)
        dialog.show()
    }
    
    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    
    private fun enterFullscreen() {
        isFullscreen = true
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        bottomBtns.visibility = View.GONE
    }
    
    private fun exitFullscreen() {
        isFullscreen = false
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        bottomBtns.visibility = View.VISIBLE
    }
    
    private fun shareImage() {
        currentImg?.let { img ->
            val url = "https://nekos.moe/image/${img.id}"
            
            Glide.with(this)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bmp: Bitmap, trans: Transition<in Bitmap>?) {
                        val cachePath = File(cacheDir, "images")
                        cachePath.mkdirs()
                        val file = File(cachePath, "share_${img.id}.jpg")
                        
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.provider",
                            file
                        )
                        
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newUri(contentResolver, "Image", uri)
                        clipboard.setPrimaryClip(clip)
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        startActivity(Intent.createChooser(intent, "Share image"))
                        Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                    
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }
    
    private fun startAutoNext() {
        autoNextJob?.cancel()
        val interval = prefs.autoNextInterval
        
        if (interval > 0) {
            autoNextJob = lifecycleScope.launch {
                while(isActive) {
                    delay(interval * 1000L)
                    loadNext()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        autoNextJob?.cancel()
    }
}
