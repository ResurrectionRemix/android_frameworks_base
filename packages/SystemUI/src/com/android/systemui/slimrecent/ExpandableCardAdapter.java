/*
 * Copyright (C) 2017 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.slimrecent;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.provider.ContactsContract;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.transition.AutoTransition;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.android.internal.util.slim.ColorUtils;

import com.android.systemui.R;

public class ExpandableCardAdapter extends RecyclerView.Adapter<ExpandableCardAdapter.ViewHolder> {

    private Context mContext;

    private ArrayList<ExpandableCard> mCards = new ArrayList<>();

    public ExpandableCardAdapter(Context context) {
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.card, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        ExpandableCard card = mCards.get(position);
        holder.screenshot.setVisibility(card.expanded ? View.VISIBLE : View.GONE);
        holder.expandButton.setRotation(card.expanded ? -180 : 0);

        if (card.customIcon) {
            holder.expandButton.setImageDrawable(card.custom);
            holder.expandButton.setOnClickListener(card.customClickListener);
        } else {
            holder.expandButton.setImageResource(R.drawable.ic_expand);
            holder.expandButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ExpandableCard expand = mCards.get(holder.getAdapterPosition());
                    expand.expanded = !expand.expanded;
                    if (card.expandListener != null) {
                        card.expandListener.onExpanded(expand.expanded);
                    }
                    Fade trans = new Fade();
                    trans.setDuration(150);
                    TransitionManager.beginDelayedTransition(
                            (ViewGroup) holder.itemView.getParent(), trans);
                    holder.expandButton.animate().rotation(expand.expanded ? -180 : 0);
                    notifyItemChanged(position);
                }
            });
        }

        holder.expandButton.setVisibility(
                (card.expandVisible || card.customIcon) ? View.VISIBLE : View.GONE);

        if (card.cardClickListener != null) {
            holder.itemView.setOnClickListener(card.cardClickListener);
        }

        if (card.cardBackgroundColor != 0) {
            holder.card.setCardBackgroundColor(card.cardBackgroundColor);
            int color;
            if (ColorUtils.isDarkColor(card.cardBackgroundColor)) {
                color = mContext.getColor(R.color.recents_task_bar_light_text_color);
            } else {
                color = mContext.getColor(R.color.recents_task_bar_dark_text_color);
            }
            holder.appName.setTextColor(color);
            holder.expandButton.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }

        holder.hideOptions(-1, -1);
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mCards.get(holder.getAdapterPosition()).optionsShown = true;
                int[] temp = new int[2];
                v.getLocationOnScreen(temp);
                int x = holder.upX - temp[0];
                int y = holder.upY - temp[1];
                holder.showOptions(x, y);
                return true;
            }
        });

        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                holder.upX = (int) event.getRawX();
                holder.upY = (int) event.getRawY();
                return false;
            }
        });

        if (card.appIcon != null) {
            holder.appIcon.setImageDrawable(card.appIcon);
        } else {
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        if (card.appIconLongClickListener != null) {
            holder.appIcon.setOnLongClickListener(card.appIconLongClickListener);
        }

        holder.favorite.setVisibility(card.favorite ? View.VISIBLE : View.GONE);

        holder.appName.setText(card.appName);

        if (card.screenshot != null && !card.screenshot.isRecycled()) {
            holder.screenshot.setImageBitmap(card.screenshot);
        }

        LayoutInflater inflater = LayoutInflater.from(mContext);
        int backgroundColor = holder.card.getCardBackgroundColor().getDefaultColor();
        if (ColorUtils.isDarkColor(backgroundColor)) {
            holder.optionsView.setBackgroundColor(ColorUtils.lightenColor(backgroundColor));
        } else {
            holder.optionsView.setBackgroundColor(ColorUtils.darkenColor(backgroundColor));
        }
        holder.optionsView.removeAllViewsInLayout();
        for (final OptionsItem item : mCards.get(position).mOptions) {
            ImageView option = (ImageView) inflater.inflate(
                    R.layout.options_item, holder.optionsView, false);
            option.setImageDrawable(item.icon);
            option.setId(item.id);
                option.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (item.clickListener != null) {
                            item.clickListener.onClick(v);
                        }
                        mCards.get(holder.getAdapterPosition()).optionsShown = false;
                        int[] temp = new int[2];
                        v.getLocationOnScreen(temp);
                        int x = holder.upX - temp[0];
                        int y = holder.upY - temp[1];
                        holder.hideOptions(x, y);
                    }
                });
            holder.optionsView.addView(option);
        }
    }

    public void clearCards() {
        mCards.clear();
    }

    public ExpandableCard getCard(int pos) {
        return mCards.get(pos);
    }

    public void addCard(ExpandableCard card) {
        mCards.add(card);
        notifyItemInserted(mCards.indexOf(card));
    }

    public void removeCard(ExpandableCard card) {
        removeCard(mCards.indexOf(card));
    }

    public void removeCard(int pos)  {
        mCards.remove(pos);
        notifyItemRemoved(pos);
        notifyItemRangeChanged(pos, getItemCount());
    }

    @Override
    public int getItemCount() {
        return mCards.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView screenshot;
        ImageView appIcon;
        ImageView favorite;
        TextView appName;
        ImageView expandButton;
        CardView card;
        LinearLayout cardContent;
        LinearLayout optionsView;

        private int upX;
        private int upY;

        public ViewHolder(View itemView) {
            super(itemView);
            cardContent = (LinearLayout) itemView.findViewById(R.id.card_content);
            appIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            favorite = (ImageView) itemView.findViewById(R.id.favorite_icon);
            appName = (TextView) itemView.findViewById(R.id.app_name);
            appName.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            screenshot = (ImageView) itemView.findViewById(R.id.screenshot);
            expandButton = (ImageView) itemView.findViewById(R.id.expand);
            card = (CardView) itemView.findViewById(R.id.card);
            optionsView = (LinearLayout) itemView.findViewById(R.id.card_options);
        }

        void showOptions(int x, int y) {
            if (x == -1 || y == -1) {
                optionsView.setVisibility(View.VISIBLE);
                return;
            }

            final double horz = Math.max(itemView.getWidth() - x, x);
            final double vert = Math.max(itemView.getHeight() - y, y);
            final float r = (float) Math.hypot(horz, vert);

            final Animator a = ViewAnimationUtils.createCircularReveal(optionsView, x, y, 0, r);
            a.setDuration(700);
            a.setInterpolator(new PathInterpolator(0f, 0f, 0.2f, 1f));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);

                }
            });
            optionsView.setVisibility(View.VISIBLE);
            a.start();
        }

        void hideOptions(int x, int y) {
            if (x == -1 || y == -1) {
                optionsView.setVisibility(View.GONE);
                return;
            }

            final double horz = Math.max(itemView.getWidth() - x, x);
            final double vert = Math.max(itemView.getHeight() - y, y);
            final float r = (float) Math.hypot(horz, vert);

            final Animator a = ViewAnimationUtils.createCircularReveal(optionsView, x, y, r, 0);
            a.setDuration(700);
            a.setInterpolator(new PathInterpolator(0f, 0f, 0.2f, 1f));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    optionsView.setVisibility(View.GONE);
                }
            });
            a.start();
        }
    }

    public interface ExpandListener {
        void onExpanded(boolean expanded);
    }

    public static class ExpandableCard {
        boolean expanded = false;
        String appName;
        Drawable appIcon;
        Bitmap screenshot;
        private ArrayList<OptionsItem> mOptions = new ArrayList<>();
        boolean optionsShown = false;
        boolean expandVisible = true;
        boolean customIcon = false;
        boolean favorite = false;
        View.OnLongClickListener appIconLongClickListener;
        int cardBackgroundColor;
        Drawable custom;
        View.OnClickListener customClickListener;
        View.OnClickListener cardClickListener;
        ExpandListener expandListener;

        public ExpandableCard(String appName, Drawable appIcon) {
            this.appName = appName;
            this.appIcon = appIcon;
        }

        public void addOption(OptionsItem item) {
            mOptions.add(item);
        }

        public void clearOptions() {
            mOptions.clear();
        }

        public void setCardClickListener(View.OnClickListener listener) {
            cardClickListener = listener;
        }

        public void setCustomClick(Drawable icon, View.OnClickListener listener) {
            customIcon = true;
            custom = icon;
            customClickListener = listener;
        }
    }

    public static class OptionsItem {
        int id;
        Drawable icon;
        View.OnClickListener clickListener;
        boolean finishIcon = false;

        public OptionsItem(Drawable icon, int id, View.OnClickListener clickListener) {
            this.icon = icon;
            this.id = id;
            this.clickListener = clickListener;
        }

        public OptionsItem(Drawable icon, int id, boolean finish) {
            this(icon, id, null);
            this.finishIcon = finish;
        }
    }
}
