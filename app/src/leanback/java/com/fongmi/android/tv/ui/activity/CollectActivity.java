package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;
    private final Set<String> mBlockedSites = new HashSet<>(Setting.getBlockedSites());
    private final Map<String, List<Vod>> mSiteResults = new HashMap<>();

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment(int position) {
        return (CollectFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, position);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        mSiteResults.clear();
        mBlockedSites.clear();
        mBlockedSites.addAll(Setting.getBlockedSites());
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
        mAdapter.setOnDoubleClickListener((position, item) -> toggleBlock(position, item));
    }

    private void setRecyclerView() {
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (result.getList().isEmpty()) return;
            String siteKey = result.getVod().getSite().getKey();
            List<Vod> siteList = mSiteResults.computeIfAbsent(siteKey, k -> new ArrayList<>());
            siteList.addAll(result.getList());
            int existingIndex = findSiteIndex(siteKey);
            if (existingIndex >= 0) {
                Collect existing = mAdapter.get(existingIndex);
                existing.getList().addAll(result.getList());
                mAdapter.notifyItemChanged(existingIndex);
            } else {
                Collect newCollect = Collect.create(result.getList());
                newCollect.setBlocked(mBlockedSites.contains(siteKey));
                mAdapter.add(newCollect);
            }
            rebuildAllItems();
            refreshAllFragment();
            mBinding.pager.getAdapter().notifyDataSetChanged();
        });
    }

    private void refreshAllFragment() {
        if (mAdapter.getItemCount() == 0) return;
        if (mBinding.pager.getCurrentItem() == 0) {
            try {
                CollectFragment fragment = getFragment(0);
                Collect allItem = mAdapter.get(0);
                fragment.updateData(allItem.getList());
            } catch (Exception ignored) {
            }
        }
    }

    private int findSiteIndex(String siteKey) {
        for (int i = 0; i < mAdapter.getItemCount(); i++) {
            if (mAdapter.get(i).getSite().getKey().equals(siteKey)) return i;
        }
        return -1;
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        Set<String> blockedTags = Setting.getBlockedTags();
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).filter(site -> Collections.disjoint(site.getTags(), blockedTags)).toList();
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mAdapter.add(Collect.all());
        mBinding.pager.getAdapter().notifyDataSetChanged();
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private void toggleBlock(int position, Collect item) {
        if (position == 0) return;
        Site site = item.getSite();
        if ("all".equals(site.getKey())) return;
        boolean isBlocked = !mBlockedSites.contains(site.getKey());
        if (isBlocked) {
            mBlockedSites.add(site.getKey());
            item.setBlocked(true);
        } else {
            mBlockedSites.remove(site.getKey());
            item.setBlocked(false);
        }
        Setting.putBlockedSites(mBlockedSites);
        mAdapter.notifyItemChanged(position);
        rebuildAllItems();
        refreshAllFragment();
        mBinding.pager.getAdapter().notifyDataSetChanged();
    }

    private void rebuildAllItems() {
        if (mAdapter.getItemCount() == 0) return;
        Collect allItem = mAdapter.get(0);
        List<Vod> allList = new ArrayList<>();
        for (Map.Entry<String, List<Vod>> entry : mSiteResults.entrySet()) {
            if (!mBlockedSites.contains(entry.getKey())) {
                allList.addAll(entry.getValue());
            }
        }
        allItem.getList().clear();
        allItem.getList().addAll(allList);
        for (int i = 1; i < mAdapter.getItemCount(); i++) {
            Collect c = mAdapter.get(i);
            c.setBlocked(mBlockedSites.contains(c.getSite().getKey()));
        }
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Collect collect = mAdapter.get(position);
            collect.setBlocked(mBlockedSites.contains(collect.getSite().getKey()));
            if ("all".equals(collect.getSite().getKey())) {
                List<Vod> filteredList = new ArrayList<>();
                for (Map.Entry<String, List<Vod>> entry : mSiteResults.entrySet()) {
                    if (!mBlockedSites.contains(entry.getKey())) {
                        filteredList.addAll(entry.getValue());
                    }
                }
                collect = new Collect(collect.getSite(), filteredList);
                collect.setBlocked(false);
            }
            return CollectFragment.newInstance(getKeyword(), collect);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}