package com.leeduc.watermarkcam

import android.net.Uri
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

/** Backs the review ViewPager2: one zoomable, pannable photo per page, loaded from its MediaStore Uri. */
class ReviewPagerAdapter(
    private val uris: MutableList<Uri>
) : RecyclerView.Adapter<ReviewPagerAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val photoView = PhotoView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return PhotoViewHolder(photoView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        Glide.with(holder.photoView).load(uris[position]).into(holder.photoView)
    }

    override fun getItemCount(): Int = uris.size

    fun uriAt(position: Int): Uri? = uris.getOrNull(position)

    fun removeAt(position: Int) {
        uris.removeAt(position)
        notifyItemRemoved(position)
    }
}
