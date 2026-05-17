package com.termux.zerocore.ai.llm.data;

import android.content.Context;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.zerocore.ai.llm.markdown.MarkDownAPI;
import com.termux.zerocore.ai.llm.utils.SpannableTextUtil;
import com.termux.zerocore.ai.model.ProviderStreamEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private List<ChatMessage> messages;
    private Context context;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? R.layout.item_chat_message_user : R.layout.item_chat_message_bot;
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new ChatMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        Markwon markwon = Markwon.builder(context).usePlugin(MarkDownAPI.create(context)).build();
        Spanned markdown = markwon.toMarkdown(message.getMessageText());
        Spanned finalSpanned = SpannableTextUtil.createClickableSpannableString(markdown, context);
        markwon.setParsedMarkdown(holder.messageTextView, finalSpanned);
        holder.messageTextView.setMovementMethod(LinkMovementMethod.getInstance());

        bindOptionalText(holder.reasoningTextView, "Reasoning\n" + message.getReasoningText(), message.getReasoningText());
        bindOptionalText(holder.toolTextView, "\u5de5\u5177\u8c03\u7528\uff1a"
            + ProviderStreamEvent.TOOL_DISPLAY_ONLY_NOTICE + "\n"
            + message.getToolCallsJson(), message.getToolCallsJson());
        holder.timeTextView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(message.getTimestamp())));
    }

    private void bindOptionalText(TextView view, String display, String raw) {
        if (view == null) return;
        if (raw == null || raw.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(display);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void release() { MarkDownAPI.create(context).release(); }

    public static class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timeTextView;
        TextView reasoningTextView;
        TextView toolTextView;

        public ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            reasoningTextView = itemView.findViewById(R.id.reasoningTextView);
            toolTextView = itemView.findViewById(R.id.toolTextView);
        }
    }

    public void updateMessageText(int position, String additionalText) {
        ChatMessage message = messages.get(position);
        message.appendMessageText(additionalText);
        notifyItemChanged(position);
    }

    public void updateReasoningText(int position, String additionalText) {
        ChatMessage message = messages.get(position);
        message.appendReasoningText(additionalText);
        notifyItemChanged(position);
    }

    public void updateToolCallsText(int position, String additionalText) {
        ChatMessage message = messages.get(position);
        message.appendToolCallsJson(additionalText);
        notifyItemChanged(position);
    }
}
