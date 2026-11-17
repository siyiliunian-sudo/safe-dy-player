package com.xiazongji.littlestar;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two-column local video grid styled like the search result cards. */
public final class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.Holder> {
    public interface OnClick {
        void onVideoClick(VideoItem item);
    }

    private final Context context;
    private final List<VideoItem> videos = new ArrayList<>();
    private final OnClick onClick;

    public VideoGridAdapter(Context context, OnClick onClick) {
        this.context = context;
        this.onClick = onClick;
    }

    public void submit(List<VideoItem> items) {
        videos.clear();
        videos.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context)
                .inflate(R.layout.item_grid_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        VideoItem item = videos.get(position);
        holder.title.setText(item.title());
        holder.duration.setText(VideoItem.formatDuration(item.durationMs));
        holder.author.setText(context.getString(R.string.grid_author));
        holder.meta.setText(formatCount(item));
        Bitmap bitmap = VideoStore.decodeThumb(item.thumbPath, 640);
        if (bitmap != null) {
            holder.thumb.setPadding(0, 0, 0, 0);
            holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.thumb.clearColorFilter();
            holder.thumb.setImageBitmap(bitmap);
        } else {
            int pad = Math.round(56 * context.getResources().getDisplayMetrics().density);
            holder.thumb.setPadding(pad, pad, pad, pad);
            holder.thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.thumb.setImageResource(R.drawable.ic_photo);
            holder.thumb.setColorFilter(0xFFC6C6CC);
        }
        holder.itemView.setOnClickListener(v -> onClick.onVideoClick(item));
    }

    private String formatCount(VideoItem item) {
        long hash = Math.abs((long) item.title().hashCode());
        long value = 1200L + hash % 210000L;
        if (value >= 10000L) {
            return String.format(Locale.CHINA, "%.1f万", value / 10000f);
        }
        return String.valueOf(value);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView duration;
        final TextView author;
        final TextView meta;

        Holder(View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.gridThumb);
            title = itemView.findViewById(R.id.gridTitle);
            duration = itemView.findViewById(R.id.gridDuration);
            author = itemView.findViewById(R.id.gridAuthor);
            meta = itemView.findViewById(R.id.gridMeta);
        }
    }
}
