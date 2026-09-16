package com.fongmi.android.tv.ui.dialog;

import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private static final int GRID_COUNT = 10;
    private static final long DOUBLE_CLICK_INTERVAL = 400;

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private boolean action;
    private int type;
    private String searchText = "";
    private String selectedTag = "";
    private long lastTagClickTime;
    private String lastTagClick;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        action = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        if (activity instanceof SiteListener) listener = (SiteListener) activity;
    }

    private boolean list() {
        return Setting.getSiteMode() == 0 || adapter.getItemCount() < GRID_COUNT;
    }

    private int getCount() {
        return list() ? 1 : MathUtils.clamp((int) Math.ceil((double) adapter.getItemCount() / GRID_COUNT), 2, 3);
    }

    private int getIcon() {
        return list() ? R.drawable.ic_site_grid : R.drawable.ic_site_list;
    }

    private float getWidth() {
        return 0.4f + (getCount() - 1) * 0.2f;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SiteAdapter(this);
        if (action) binding.action.setVisibility(View.VISIBLE);
        setType(type);
        setRecyclerView();
        setMode();
        initSearch();
        initTags();
    }

    private void initSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString().trim().toLowerCase();
                filterSites();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void initTags() {
        List<String> tags = extractTags();
        if (tags.isEmpty()) {
            binding.tagScroll.setVisibility(View.GONE);
            return;
        }
        binding.tagScroll.setVisibility(View.VISIBLE);
        binding.tagGroup.removeAllViews();

        Set<String> blocked = Setting.getBlockedTags();

        Chip allChip = new Chip(requireContext());
        allChip.setText("全部");
        allChip.setCheckable(true);
        allChip.setChecked(true);
        allChip.setOnClickListener(v -> {
            selectedTag = "";
            filterSites();
        });
        binding.tagGroup.addView(allChip);

        for (String tag : tags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCheckable(true);
            setBlockedStyle(chip, blocked.contains(tag));
            chip.setOnClickListener(v -> onTagClick(tag, chip, allChip));
            binding.tagGroup.addView(chip);
        }
    }

    private void onTagClick(String tag, Chip chip, Chip allChip) {
        long now = System.currentTimeMillis();
        boolean doubleClick = tag.equals(lastTagClick) && now - lastTagClickTime < DOUBLE_CLICK_INTERVAL;
        lastTagClickTime = now;
        lastTagClick = tag;
        if (doubleClick) {
            toggleTagBlock(tag, chip, allChip);
            return;
        }
        selectedTag = chip.isChecked() ? tag : "";
        if (!chip.isChecked()) allChip.setChecked(true);
        else allChip.setChecked(false);
        filterSites();
    }

    private void toggleTagBlock(String tag, Chip chip, Chip allChip) {
        Set<String> blocked = Setting.getBlockedTags();
        if (blocked.contains(tag)) {
            blocked.remove(tag);
            Notify.show("已解除屏蔽分类：" + tag);
        } else {
            blocked.add(tag);
            Notify.show("已屏蔽分类：" + tag + "，搜索时不再显示该分类");
        }
        Setting.putBlockedTags(blocked);
        setBlockedStyle(chip, blocked.contains(tag));
        chip.setChecked(true);
        allChip.setChecked(false);
        selectedTag = tag;
        filterSites();
    }

    private void setBlockedStyle(Chip chip, boolean blocked) {
        if (blocked) {
            chip.getPaint().setFlags(chip.getPaint().getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            chip.getPaint().setAntiAlias(true);
            chip.setAlpha(0.45f);
        } else {
            chip.getPaint().setFlags(chip.getPaint().getFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            chip.getPaint().setAntiAlias(true);
            chip.setAlpha(1.0f);
        }
    }

    private List<String> extractTags() {
        Set<String> tagSet = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("\\[([^\\]]+)]");
        for (Site site : VodConfig.get().getSites()) {
            if (site.isHide()) continue;
            Matcher matcher = pattern.matcher(site.getName());
            while (matcher.find()) {
                tagSet.add(matcher.group(1));
            }
        }
        return new ArrayList<>(tagSet);
    }

    private void filterSites() {
        adapter.filter(searchText, selectedTag);
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(this::onMode);
        binding.select.setOnClickListener(v -> adapter.selectAll());
        binding.cancel.setOnClickListener(v -> adapter.cancelAll());
        binding.search.setOnClickListener(v -> setType(v.isSelected() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isSelected() ? 0 : 2));
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration != null) binding.recycler.removeItemDecoration(decoration);
        binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), getCount()));
        if (!binding.mode.hasFocus()) binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setType(int type) {
        binding.search.setSelected(type == 1);
        binding.change.setSelected(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        adapter.setType(this.type = type);
    }

    private void setMode() {
        if (adapter.getItemCount() < GRID_COUNT) Setting.putSiteMode(0);
        binding.mode.setEnabled(adapter.getItemCount() >= GRID_COUNT);
        binding.mode.setImageResource(getIcon());
    }

    private void onMode(View view) {
        Setting.putSiteMode(Math.abs(Setting.getSiteMode() - 1));
        setRecyclerView();
        setMode();
    }

    @Override
    public void onItemClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else setWidth(getWidth());
    }

    private static class MathUtils {
        static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }
}