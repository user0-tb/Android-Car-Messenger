/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.messenger.core.ui.conversationlist;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.android.car.messenger.R;
import com.android.car.messenger.core.interfaces.AppFactory;
import com.android.car.messenger.core.interfaces.DataModel;
import com.android.car.messenger.core.shared.NotificationHandler;
import com.android.car.messenger.core.ui.conversationlist.ConversationItemAdapter.OnConversationItemClickListener;
import com.android.car.messenger.core.ui.shared.CircularOutputlineProvider;
import com.android.car.messenger.core.ui.shared.DateTimeView;
import com.android.car.messenger.core.ui.shared.ViewUtils;

/**
 * {@link RecyclerView.ViewHolder} for Conversation Log item, responsible for presenting and
 * resetting the UI on recycle.
 */
public class ConversationItemViewHolder extends RecyclerView.ViewHolder {
    @NonNull private final DataModel mDataModel;

    @NonNull
    private final ConversationItemAdapter.OnConversationItemClickListener
            mOnConversationItemClickListener;

    @NonNull private final View mPlayMessageTouchView;
    @NonNull private final ImageView mAvatarView;
    @NonNull private final TextView mTitleView;
    @NonNull private final TextView mPreviewTextView;
    @NonNull private final TextView mDotSeparatorView;
    @NonNull private final ImageView mSubtitleIconView;
    @NonNull private final ImageView mMuteActionButton;
    @NonNull private final View mReplyActionButton;
    @NonNull private final View mPlayActionButton;
    @NonNull private final TextView mUnreadBadge;
    @NonNull private final DateTimeView mDateTimeView;
    @NonNull private final View mDivider;

    /** Conversation Item View Holder constructor */
    public ConversationItemViewHolder(
            @NonNull View itemView,
            @NonNull OnConversationItemClickListener onConversationItemClickListener) {
        super(itemView);
        mOnConversationItemClickListener = onConversationItemClickListener;
        mPlayMessageTouchView = itemView.findViewById(R.id.play_action_touch_view);
        mAvatarView = itemView.findViewById(R.id.icon);
        mTitleView = itemView.findViewById(R.id.title);
        mPreviewTextView = itemView.findViewById(R.id.preview);
        mDateTimeView = itemView.findViewById(R.id.date_time_view);
        mDateTimeView.setShowRelativeTime(true);
        mDotSeparatorView = itemView.findViewById(R.id.preview_dot);
        mUnreadBadge = itemView.findViewById(R.id.unread_badge);
        mSubtitleIconView = itemView.findViewById(R.id.last_action_icon_view);
        mMuteActionButton = itemView.findViewById(R.id.mute_action_button);
        mReplyActionButton = itemView.findViewById(R.id.reply_action_button);
        mPlayActionButton = itemView.findViewById(R.id.play_action_button);
        mDivider = itemView.findViewById(R.id.divider);
        mAvatarView.setOutlineProvider(CircularOutputlineProvider.get());
        mDataModel = AppFactory.get().getDataModel();
    }

    /** Binds the view holder with relevant data. */
    public void bind(@NonNull UIConversationItem uiData) {
        mTitleView.setText(uiData.getTitle());
        mPreviewTextView.setText(uiData.getTextPreview());
        mUnreadBadge.setText(uiData.getUnreadCountText());
        mDateTimeView.setTime(uiData.mLastMessageTimestamp);
        mAvatarView.setImageDrawable(uiData.getAvatar());
        mPlayMessageTouchView.setOnClickListener(null);
        mSubtitleIconView.setImageDrawable(uiData.getSubtitleIcon());
        boolean showPreviewSeparator = !uiData.getTextPreview().isEmpty();
        ViewUtils.setVisible(mDotSeparatorView, showPreviewSeparator);
        ViewUtils.setVisible(mSubtitleIconView, uiData.getSubtitleIcon() != null);
        setUpActionButton(uiData);
        setUpTextAppearance(uiData);
        updateMuteButton(uiData.isMuted());
        itemView.setVisibility(View.VISIBLE);
    }

    private void setUpTextAppearance(@NonNull UIConversationItem uiData) {
        Context context = AppFactory.get().getContext();
        if (uiData.shouldUseUnreadTheme()) {
            mTitleView.setTextAppearance(R.style.TextAppearance_MessageHistoryUnreadTitle);
            mPreviewTextView.setTextAppearance(
                    R.style.TextAppearance_MessageHistoryTextPreviewUnread);
            mDateTimeView.setTextAppearance(R.style.TextAppearance_MessageHistoryUnreadMetadata);
            mDotSeparatorView.setTextAppearance(
                    R.style.TextAppearance_MessageHistoryTextPreviewUnread);
            updateSubtitleIcon(context.getColor(R.color.unread_color));
            mUnreadBadge.setVisibility(View.VISIBLE);
        } else {
            mTitleView.setTextAppearance(R.style.TextAppearance_MessageHistoryTitle);
            mPreviewTextView.setTextAppearance(R.style.TextAppearance_MessageHistoryTextPreview);
            mDateTimeView.setTextAppearance(R.style.TextAppearance_MessageHistoryTextPreview);
            mDotSeparatorView.setTextAppearance(R.style.TextAppearance_MessageHistoryTextPreview);
            updateSubtitleIcon(context.getColor(R.color.secondary_text_color));
            mUnreadBadge.setVisibility(View.INVISIBLE);
        }
    }

    private void updateSubtitleIcon(@ColorInt int color) {
        PorterDuffColorFilter porterDuffColorFilter =
                new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        mSubtitleIconView.getDrawable().setColorFilter(porterDuffColorFilter);
    }

    private void updateMuteButton(boolean isMuted) {
        int drawableRes = isMuted ? R.drawable.ic_unmute : R.drawable.ic_mute;
        Drawable drawable = AppFactory.get().getContext().getDrawable(drawableRes);
        mMuteActionButton.setImageDrawable(drawable);
    }

    /** Recycles views. */
    public void recycle() {
        mPlayMessageTouchView.setOnClickListener(null);
    }

    private void setUpActionButton(@NonNull UIConversationItem uiData) {
        ViewUtils.setVisible(
                mDivider,
                uiData.shouldShowReplyIcon()
                        || uiData.shouldShowMuteIcon()
                        || uiData.shouldShowPlayIcon());
        ViewUtils.setVisible(mMuteActionButton, uiData.shouldShowMuteIcon());
        ViewUtils.setVisible(mReplyActionButton, uiData.shouldShowReplyIcon());
        ViewUtils.setVisible(mPlayActionButton, uiData.shouldShowPlayIcon());
        if (uiData.shouldShowReplyIcon()) {
            mReplyActionButton.setEnabled(true);
        }
        if (uiData.shouldShowMuteIcon()) {
            mMuteActionButton.setEnabled(true);
        }
        if (uiData.shouldShowPlayIcon()) {
            mPlayActionButton.setEnabled(true);
        }

        mPlayMessageTouchView.setOnClickListener(
                view ->
                        mOnConversationItemClickListener.onConversationItemClicked(
                                uiData.getConversation()));

        mReplyActionButton.setOnClickListener(
                view ->
                        mOnConversationItemClickListener.onReplyIconClicked(
                                uiData.getConversation()));

        mPlayActionButton.setOnClickListener(
                view ->
                        mOnConversationItemClickListener.onPlayIconClicked(
                                uiData.getConversation()));
        mMuteActionButton.setOnClickListener(
                view -> {
                    boolean mute = !uiData.isMuted();
                    mDataModel.muteConversation(uiData.getConversationId(), mute);
                    if (mute) {
                        NotificationHandler.removeNotification(uiData.getConversationId());
                    }
                });
    }
}
