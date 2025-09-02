package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class GamesCoverDialogFragment extends DialogFragment {
    private boolean didInitialNudge = false;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.AppTheme);
    }
    private CoversAdapter adapter;
    private String[] titles;
    private String[] uris;
    private String[] coverUrls;
    private String[] localPaths;
    private String[] origTitles;
    private String[] origUris;
    private String[] origCoverUrls;
    private String[] origLocalPaths;
    private RecyclerView rv;
    private LinearLayoutManager llm;
    private PagerSnapHelper snapHelper;
    private int lastRvW = -1, lastRvH = -1;
    private boolean pendingResnap = false;
    private int lastItemWidthPx = 0;
    private RecyclerView rvLetters;
    private LinearLayoutManager llmLetters;
    private PagerSnapHelper lettersSnapHelper;
    private LettersAdapter lettersAdapter;
    private java.util.ArrayList<Character> letters = new java.util.ArrayList<>();
    private Character pendingLetterJump = null;
    private static final int SORT_ALPHA = 0;
    private static final int SORT_RECENT = 1;
    private int sortMode = SORT_ALPHA;
    private String query = null;
    // Simpler grouping/sort helpers (revert)
    private static char firstLetter(String t) {
        if (t == null) return '#';
        String s = t.trim();
        if (s.isEmpty()) return '#';
        char c = Character.toUpperCase(s.charAt(0));
        return Character.isLetter(c) ? c : '#';
    }

    public interface OnGameSelectedListener {
        void onGameSelected(String gameUri);
    }

    private static final String ARG_TITLES = "titles";
    private static final String ARG_URIS = "uris";

    public static GamesCoverDialogFragment newInstance(String[] titles, String[] uris) {
        GamesCoverDialogFragment f = new GamesCoverDialogFragment();
        Bundle b = new Bundle();
        b.putStringArray(ARG_TITLES, titles);
        b.putStringArray(ARG_URIS, uris);
        f.setArguments(b);
        return f;
    }

    private OnGameSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnGameSelectedListener) {
            listener = (OnGameSelectedListener) context;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rv != null) applyCoverflowTransforms(rv);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Return a styled dialog; content is provided by onCreateView
        return new Dialog(requireContext(), R.style.PSX2_FullScreenDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_covers_grid, container, false);

        rv = root.findViewById(R.id.recycler_covers);
        rv.setHasFixedSize(true);
        llm = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(llm);
        rv.setClipToPadding(false);
        int sidePad = (int) (48 * getResources().getDisplayMetrics().density);
        int vertPad = (int) (24 * getResources().getDisplayMetrics().density);
        rv.setPadding(sidePad, vertPad, sidePad, vertPad);
        // Snap to center item
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);
        // Letters row setup
        rvLetters = root.findViewById(R.id.recycler_letters);
        if (rvLetters != null) {
            rvLetters.setHasFixedSize(true);
            llmLetters = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
            rvLetters.setLayoutManager(llmLetters);
        // Snap to center item in the letters row
            lettersSnapHelper = new PagerSnapHelper();
            lettersSnapHelper.attachToRecyclerView(rvLetters);
            rvLetters.setClipToPadding(false);
            int basePad = (int)(24*getResources().getDisplayMetrics().density);
            rvLetters.setPadding(basePad, 0, basePad, 0);
            // Add spacing between letters for off-screen effect
            final int letterSpace = (int)(12 * getResources().getDisplayMetrics().density);
            rvLetters.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int pos = parent.getChildAdapterPosition(view);
                    if (pos == RecyclerView.NO_POSITION) return;
                    outRect.left = letterSpace;
                    outRect.right = letterSpace;
                }
            });
            rvLetters.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    applyLetterTransforms(recyclerView);
                }
                @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) applyLetterTransforms(recyclerView);
                }
            });
            // Initial snap + dynamic side padding after first layout to keep center item centered
            rvLetters.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    int w = rvLetters.getWidth();
                    if (w > 0) {
                        float d = getResources().getDisplayMetrics().density;
                        int itemPx = (int) (72 * d); // item_letter width
                        int letterSpace = (int) (12 * d);
                        int totalItem = itemPx + 2 * letterSpace;
                        int side = Math.max((int)(24 * d), (w - totalItem) / 2);
                        rvLetters.setPadding(side, 0, side, 0);
                    }
                    rvLetters.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    rvLetters.post(() -> { resnapLetters(); applyLetterTransforms(rvLetters); });
                }
            });
        }
        // Scale/alpha transform based on distance from center
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCoverflowTransforms(recyclerView);
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (pendingResnap) {
                        resnapToCenter(recyclerView);
                        pendingResnap = false;
                    }
                    applyCoverflowTransforms(recyclerView);
                }
            }
        });

        titles = getArguments() != null ? getArguments().getStringArray(ARG_TITLES) : new String[0];
        uris = getArguments() != null ? getArguments().getStringArray(ARG_URIS) : new String[0];
        coverUrls = new String[uris.length];
        localPaths = new String[uris.length];
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        for (int i = 0; i < uris.length; i++) {
            String saved = prefs.getString("serial:" + uris[i], null);
            String serial = saved;
            if (serial == null || serial.isEmpty()) {
                try {
                    String nativeSerial = NativeApp.getGameSerial(uris[i]);
                    if (nativeSerial != null && !nativeSerial.isEmpty()) {
                        serial = normalizeSerial(nativeSerial);
                        prefs.edit().putString("serial:" + uris[i], serial).apply();
                    }
                } catch (Throwable ignored) {}
            }
            if (serial == null || serial.isEmpty()) {
                serial = buildSerialFromUri(uris[i]);
            }
            coverUrls[i] = buildCoverUrlFromSerial(serial);
            localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
        }

        // cache originals for sorting/filtering
        origTitles = Arrays.copyOf(titles, titles.length);
        origUris = Arrays.copyOf(uris, uris.length);
        origCoverUrls = Arrays.copyOf(coverUrls, coverUrls.length);
        origLocalPaths = Arrays.copyOf(localPaths, localPaths.length);
        // restore sort pref if any
        sortMode = prefs.getInt("covers_sort_mode", SORT_ALPHA);

        adapter = new CoversAdapter(requireContext(), titles, coverUrls, localPaths, R.layout.item_coverflow,
                position -> {
                    if (listener != null && position >= 0 && position < uris.length) {
                        listener.onGameSelected(uris[position]);
                        dismissAllowingStateLoss();
                    }
                },
                position -> {
                    if (position >= 0 && position < uris.length) {
                        showGameSettings(titles[position], uris[position]);
                    }
                });
        rv.setAdapter(adapter);
        // Build letters list if row present (RecyclerView variant)
        if (rvLetters != null) buildLettersAndBind();
        
        // Hook sort/search buttons if present
        View btnSortV = root.findViewById(R.id.btn_sort);
        if (btnSortV instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton btnSort = (com.google.android.material.button.MaterialButton) btnSortV;
            updateSortButtonUi(btnSort);
            btnSort.setOnClickListener(v -> {
                sortMode = (sortMode == SORT_ALPHA) ? SORT_RECENT : SORT_ALPHA;
                requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("covers_sort_mode", sortMode).apply();
                applyFilterAndSort();
                updateSortButtonUi(btnSort);
            });
        }
        View btnSearch = root.findViewById(R.id.btn_search);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> showSearchDialog());
        }
        // apply initial sort/filter if needed
        if (sortMode != SORT_ALPHA || (query != null && !query.isEmpty())) {
            applyFilterAndSort();
        }
        // One-time tiny nudge to force snap/transform on some devices
        rv.post(() -> {
            if (!isAdded() || didInitialNudge) return;
            // Anchor to a large middle position for "infinite" scroll
            int n = titles != null ? titles.length : 0;
            if (n > 0) {
                int center = (1 << 29); // ~536 million
                int startPos = center - (center % n);
                llm.scrollToPosition(startPos);
            }
            rv.scrollBy(1, 0);
            rv.scrollBy(-1, 0);
            applyCoverflowTransforms(rv);
            didInitialNudge = true;
        });
        // Reduce resize flicker and keep a few views ready
        rv.setItemAnimator(null);
        rv.setItemViewCacheSize(12);

        // Ensure initial measurement + transforms run after first layout
        rv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!isAdded()) return;
                applyCoverflowTransforms(rv);
                rv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        // Resolve proper game titles using local YAML index if available (GameIndex/Redump).
        // Falls back to native URI API, then filename if needed.
        new Thread(() -> {
            boolean changed = false;
            for (int i = 0; i < uris.length; i++) {
                try {
                    String t = TitleResolver.resolveTitleForUri(requireContext(), uris[i], titles[i]);
                    if (t != null && !t.isEmpty() && i < titles.length && !t.equals(titles[i])) {
                        titles[i] = t;
                        if (origTitles != null && i < origTitles.length) origTitles[i] = t;
                        changed = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (changed && isAdded()) requireActivity().runOnUiThread(() -> {
                if (sortMode != SORT_ALPHA || (query != null && !query.isEmpty())) {
                    applyFilterAndSort();
                } else {
                    adapter.notifyDataSetChanged();
                    if (rvLetters != null) buildLettersAndBind();
                }
            });
        }).start();

        // Dynamically size items based on RecyclerView size and orientation
        rv.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int rvW = right - left;
            final int rvH = bottom - top;
            if (rvW <= 0 || rvH <= 0) return;
            if (rvW == lastRvW && rvH == lastRvH) return; // no real size change
            lastRvW = rvW;
            lastRvH = rvH;
            rv.post(() -> {
                if (!isAdded()) return;
                boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int titleDp = 36;
                int extraDp = 16;
                float density = getResources().getDisplayMetrics().density;
                int reservedH = (int) ((titleDp + extraDp) * density) + (vertPad * 2);
                int availableH = Math.max(0, rvH - reservedH);
                float ratio = 567f / 878f;
                int widthFromHeight = (int) (availableH * ratio);
                int widthFromWidth = (int) (rvW * (landscape ? 0.35f : 0.55f));
                int itemWidth = Math.max(160, Math.min(widthFromHeight, widthFromWidth));
                lastItemWidthPx = itemWidth;
                adapter.setItemWidthPx(itemWidth);
                // Center vertically in portrait by adjusting top/bottom padding
                if (!landscape) {
                    int imageH = (int) (itemWidth / (567f / 878f));
                    int titlePx = (int) ((titleDp + extraDp) * density);
                    int contentH = imageH + titlePx;
                    int desiredPad = Math.max(vertPad, Math.max(0, (rvH - contentH) / 2));
                    rv.setPadding(sidePad, desiredPad, sidePad, desiredPad);
                } else {
                    rv.setPadding(sidePad, vertPad, sidePad, vertPad);
                }
                // If user is scrolling, defer resnap until idle to avoid fighting gesture
                if (rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    resnapToCenter(rv);
                    applyCoverflowTransforms(rv);
                } else {
                    pendingResnap = true;
                }
            });
        });

        View btnHome = root.findViewById(R.id.btn_home);
        if (btnHome != null) btnHome.setOnClickListener(v -> dismissAllowingStateLoss());
        View btnDownload = root.findViewById(R.id.btn_download);
        if (btnDownload != null) btnDownload.setOnClickListener(v -> startDownloadCovers());

        return root;
    }

    private void buildLettersAndBind() {
        letters.clear();
        boolean hasHash = false;
        boolean[] present = new boolean[26];
        if (titles != null) {
            for (String t : titles) {
                if (t == null) continue;
                char c = firstLetter(t);
                if (c >= 'A' && c <= 'Z') present[c - 'A'] = true;
                else hasHash = true;
            }
        }
        if (hasHash) letters.add('#');
        for (int i = 0; i < 26; i++) if (present[i]) letters.add((char)('A'+i));
        if (rvLetters != null) {
            lettersAdapter = new LettersAdapter(letters, this::onLetterTapped);
            rvLetters.setAdapter(lettersAdapter);
            // Anchor to a large middle position for infinite wrap-around
            int n = letters.size();
            if (n > 0) {
                int center = (1 << 29);
                int startPos = center - (center % n);
                llmLetters.scrollToPosition(startPos);
            }
            rvLetters.post(() -> { resnapLetters(); applyLetterTransforms(rvLetters); });
        }
    }

    private void applyLetterTransforms(@NonNull RecyclerView recyclerView) {
        int rvCenterX = (recyclerView.getLeft() + recyclerView.getRight()) / 2;
        final float maxScale = 1.0f;
        final float minScale = 0.85f;
        final float maxAlpha = 1.0f;
        final float minAlpha = 0.6f;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childCenterX = (child.getLeft() + child.getRight()) / 2;
            float dx = Math.abs(childCenterX - rvCenterX);
            float norm = Math.min(1f, dx / (recyclerView.getWidth() * 0.5f));
            float scale = maxScale - (maxScale - minScale) * norm;
            float alpha = maxAlpha - (maxAlpha - minAlpha) * norm;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
        }
    }

    private void buildLetterIndexLinear(@NonNull LinearLayout container) {
        container.removeAllViews();
        // Build present letters with normalization
        java.util.LinkedHashSet<Character> set = new java.util.LinkedHashSet<>();
        if (titles != null) {
            for (String t : titles) {
                char c = firstLetter(t);
                if (c == '#') { set.add('#'); }
                else if (c >= 'A' && c <= 'Z') set.add(c);
            }
        }
        float d = getResources().getDisplayMetrics().density;
        int padH = (int) (14 * d);
        int padV = (int) (6 * d);
        for (Character ch : set) {
            TextView tv = new TextView(requireContext());
            tv.setText(String.valueOf(ch));
            tv.setTextSize(18);
            tv.setTextColor(getResources().getColor(R.color.brand_primary));
            tv.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int)(8*d), 0, (int)(8*d), 0); // extra spacing
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> onLetterTapped(ch));
            container.addView(tv);
        }
    }

    private void onLetterTapped(char letter) {
        android.util.Log.d("LetterTap", "Letter tapped: " + letter + ", sortMode: " + sortMode + ", titles.length: " + (titles != null ? titles.length : 0));
        // Force A–Z sorting for predictable letter navigation
        if (sortMode != SORT_ALPHA) {
            android.util.Log.d("LetterTap", "Switching to ALPHA sort mode");
            sortMode = SORT_ALPHA;
            requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("covers_sort_mode", sortMode).apply();
            pendingLetterJump = letter;
            applyFilterAndSort();
        } else {
            android.util.Log.d("LetterTap", "Already in ALPHA mode, jumping to letter");
            // Jump to first index for that letter in the current (alphabetical) list
            jumpToLetter(letter);
        }
        // Center letters row on selected
        if (rvLetters != null && letters != null && !letters.isEmpty()) {
            int idx = letters.indexOf(letter);
            if (idx >= 0) {
                centerLettersOnIndex(idx);
            }
        }
    }

    private void centerLettersOnIndex(int idx) {
        if (rvLetters == null || llmLetters == null) return;
        View snapView = lettersSnapHelper != null ? lettersSnapHelper.findSnapView(llmLetters) : null;
        int centerPos = (snapView != null) ? rvLetters.getChildAdapterPosition(snapView) : llmLetters.findFirstVisibleItemPosition();
        if (centerPos == RecyclerView.NO_POSITION) centerPos = 0;
        int n = letters != null ? letters.size() : 0;
        if (n <= 0) return;
        int centerIdx = centerPos % n;
        int forward = (idx - centerIdx + n) % n;
        int backward = (centerIdx - idx + n) % n;
        int delta = (forward <= backward) ? forward : -backward;
        llmLetters.scrollToPosition(centerPos + delta);
        rvLetters.post(() -> { resnapLetters(); applyLetterTransforms(rvLetters); });
    }

    private void resnapLetters() {
        if (rvLetters == null || llmLetters == null || lettersSnapHelper == null) return;
        View snap = lettersSnapHelper.findSnapView(llmLetters);
        if (snap == null) return;
        int[] dist = lettersSnapHelper.calculateDistanceToFinalSnap(llmLetters, snap);
        if (dist != null && (dist[0] != 0 || dist[1] != 0)) rvLetters.scrollBy(dist[0], dist[1]);
    }

    private void updateSortButtonUi(@NonNull com.google.android.material.button.MaterialButton btn) {
        btn.setIconResource(R.drawable.sort_24px);
        boolean alpha = (sortMode == SORT_ALPHA);
        btn.setText(alpha ? "A–Z" : "RECENT");
        btn.setContentDescription(alpha ? "Sort A–Z" : "Sort Recent");
    }

    private void showSearchDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("Search games");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Search")
                .setView(input)
                .setNegativeButton("Clear", (d, w) -> {
                    query = null;
                    applyFilterAndSort();
                })
                .setPositiveButton("Apply", (d, w) -> {
                    query = input.getText() != null ? input.getText().toString().trim() : null;
                    if (query != null && query.isEmpty()) query = null;
                    applyFilterAndSort();
                })
                .show();
    }

    private void jumpToLetter(char letterRaw) {
        android.util.Log.d("LetterJump", "jumpToLetter called with: " + letterRaw);
        if (titles == null || titles.length == 0) {
            android.util.Log.d("LetterJump", "No titles available");
            return;
        }
        char letter = Character.toUpperCase(letterRaw);
        int target = -1;
        for (int i = 0; i < titles.length; i++) {
            char c = firstLetter(titles[i]);
            if ((letter == '#') ? (c == '#') : (c == letter)) { 
                target = i; 
                android.util.Log.d("LetterJump", "Found target at index " + i + " for letter " + letter + ", title: " + titles[i]);
                break; 
            }
        }
        if (target >= 0) {
            android.util.Log.d("LetterJump", "Scrolling to index: " + target);
            scrollToIndex(target);
        } else {
            android.util.Log.d("LetterJump", "No target found for letter: " + letter);
        }
    }

    private void scrollToIndex(int idx) {
        if (llm == null || rv == null || titles == null || titles.length == 0) return;
        View snap = snapHelper != null ? snapHelper.findSnapView(llm) : null;
        int centerPos = (snap != null) ? rv.getChildAdapterPosition(snap) : llm.findFirstVisibleItemPosition();
        if (centerPos == RecyclerView.NO_POSITION) centerPos = 0;
        int n = titles.length;
        int centerIdx = centerPos % n;
        int forward = (idx - centerIdx + n) % n;
        int backward = (centerIdx - idx + n) % n;
        int delta = (forward <= backward) ? forward : -backward;
        llm.scrollToPosition(centerPos + delta);
        rv.post(() -> { resnapToCenter(rv); applyCoverflowTransforms(rv); });
    }

    private void applyFilterAndSort() {
        int n = origTitles != null ? origTitles.length : 0;
        ArrayList<Integer> idxs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (query == null || query.isEmpty()) {
                idxs.add(i);
            } else {
                String t = origTitles[i] != null ? origTitles[i] : "";
                if (t.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) idxs.add(i);
            }
        }
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        if (sortMode == SORT_RECENT) {
            idxs.sort((a, b) -> {
                long ta = prefs.getLong("last_played:" + origUris[a], 0L);
                long tb = prefs.getLong("last_played:" + origUris[b], 0L);
                if (ta == tb) return origTitles[a].compareToIgnoreCase(origTitles[b]);
                return Long.compare(tb, ta);
            });
        } else {
            idxs.sort(Comparator.comparing(i -> {
                String t = origTitles[i];
                return t == null ? "" : t.toLowerCase(Locale.ROOT);
            }));
        }
        titles = new String[idxs.size()];
        uris = new String[idxs.size()];
        coverUrls = new String[idxs.size()];
        localPaths = new String[idxs.size()];
        for (int k = 0; k < idxs.size(); k++) {
            int i = idxs.get(k);
            titles[k] = origTitles[i];
            uris[k] = origUris[i];
            coverUrls[k] = origCoverUrls[i];
            localPaths[k] = origLocalPaths[i];
        }
        adapter = new CoversAdapter(requireContext(), titles, coverUrls, localPaths, R.layout.item_coverflow,
                position -> {
                    if (listener != null && position >= 0 && position < uris.length) {
                        listener.onGameSelected(uris[position]);
                        dismissAllowingStateLoss();
                    }
                },
                position -> {
                    if (position >= 0 && position < uris.length) {
                        showGameSettings(titles[position], uris[position]);
                    }
                });
        rv.setAdapter(adapter);
        if (lastItemWidthPx > 0) adapter.setItemWidthPx(lastItemWidthPx);
        int n2 = titles.length;
        if (n2 > 0) {
            int center = (1 << 29);
            int startPos = center - (center % n2);
            llm.scrollToPosition(startPos);
        }
        rv.post(() -> { resnapToCenter(rv); applyCoverflowTransforms(rv); });
        if (rvLetters != null) {
            buildLettersAndBind();
            if (pendingLetterJump != null) {
                final char l = pendingLetterJump;
                pendingLetterJump = null;
                rv.postDelayed(() -> {
                    jumpToLetter(l);
                    if (letters != null) {
                        int idx = letters.indexOf(l);
                        if (idx >= 0) centerLettersOnIndex(idx);
                    }
                }, 10);
            }
        }
    }

    private void applyCoverflowTransforms(@NonNull RecyclerView recyclerView) {
        int rvCenterX = (recyclerView.getLeft() + recyclerView.getRight()) / 2;
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        final float maxScale = 1.0f;
        final float minScale = landscape ? 0.70f : 0.82f;
        final float maxAlpha = 1.0f;
        final float minAlpha = landscape ? 0.50f : 0.60f;
        final float maxTiltDeg = landscape ? 22f : 16f;
        final float density = getResources().getDisplayMetrics().density;
        final float maxParallax = (landscape ? 14f : 18f) * density;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childCenterX = (child.getLeft() + child.getRight()) / 2;
            float dx = childCenterX - rvCenterX;
            float dist = Math.abs(dx);
            float norm = Math.min(1f, dist / (recyclerView.getWidth() * 0.5f));
            float scale = maxScale - (maxScale - minScale) * norm;
            float alpha = maxAlpha - (maxAlpha - minAlpha) * norm;
            float tilt = Math.signum(dx) * maxTiltDeg * norm; // tilt away from center
            child.setCameraDistance(8000f * density);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
            child.setTranslationZ((1f - norm) * 10f);
            child.setRotationY(tilt);

            // Title parallax (gentle)
            View title = child.findViewById(R.id.text_title);
            if (title != null) {
                float parallax = Math.max(-maxParallax, Math.min(maxParallax, -dx / (recyclerView.getWidth() * 0.5f) * maxParallax));
                title.setTranslationX(parallax);
                title.setAlpha(0.85f + 0.15f * (1f - norm));
            }

            // Shadow intensity scales with centeredness
            View shadow = child.findViewById(R.id.view_shadow);
            if (shadow != null) {
                shadow.setAlpha((1f - norm) * 0.7f);
                shadow.setScaleX(scale + 0.2f);
            }
        }
    }

    private void resnapToCenter(@NonNull RecyclerView rv) {
        if (snapHelper == null || llm == null) return;
        View snapView = snapHelper.findSnapView(llm);
        if (snapView == null) return;
        int[] dist = snapHelper.calculateDistanceToFinalSnap(llm, snapView);
        if (dist != null && (dist[0] != 0 || dist[1] != 0)) {
            rv.scrollBy(dist[0], dist[1]);
        }
    }

    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d == null) return;
        Window w = d.getWindow();
        if (w == null) return;
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility(flags);
        }
    }

    private int calculateSpanForWidth(int rvWidthPx, int itemDp, int spacingPx) {
        float density = getResources().getDisplayMetrics().density;
        int usable = Math.max(0, rvWidthPx);
        int itemPx = (int) (itemDp * density);
        // Include spacing in the packing calculation to avoid oscillation
        // span = floor((usable + spacing) / (itemPx + spacing))
        int span = (itemPx > 0) ? (int) Math.floor((usable + (double) spacingPx) / (itemPx + (double) spacingPx)) : 1;
        return Math.max(2, Math.max(1, span));
    }

    private void preloadCovers(String[] urls) {
        // Use Glide to warm cache
        for (String url : urls) {
            if (url == null) continue;
            com.bumptech.glide.Glide.with(requireContext()).load(url).preload();
        }
    }

    private void startDownloadCovers() {
        Toast.makeText(requireContext(), "Downloading covers in background", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // Try to refine serials/URLs by scanning disc contents first
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < uris.length; i++) {
                try {
                    // Prefer native serial extraction so CHDs work
                    String better = null;
                    try { better = NativeApp.getGameSerial(uris[i]); } catch (Throwable ignored) {}
                    if (better == null) better = extractSerialFromUri(uris[i]);
                    if (better != null && !better.equalsIgnoreCase(serialFromUrl(coverUrls[i]))) {
                        String serial = normalizeSerial(better);
                        coverUrls[i] = buildCoverUrlFromSerial(serial);
                        localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
                        editor.putString("serial:" + uris[i], serial);
                    }
                } catch (Exception ignored) { }
            }
            editor.apply();

            int total = coverUrls.length;
            int ok = 0;
            java.io.File dir = getCoversDir();
            if (!dir.exists()) dir.mkdirs();
            for (int i = 0; i < total; i++) {
                String url = coverUrls[i];
                String outPath = localPaths[i];
                if (isFileValid(outPath)) { ok++; continue; }
                try {
                    if (downloadToFile(url, outPath)) ok++;
                } catch (Exception ignored) { }
            }
            final int downloaded = ok;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Covers ready: " + downloaded + "/" + total, Toast.LENGTH_SHORT).show();
                // refresh adapter to prefer local files now
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private static String serialFromUrl(String url) {
        if (url == null) return null;
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (slash >= 0 && dot > slash) return url.substring(slash + 1, dot);
        return null;
    }

    private java.io.File getCoversDir() {
        java.io.File base = requireContext().getExternalFilesDir("covers");
        if (base == null) base = new java.io.File(requireContext().getFilesDir(), "covers");
        return base;
    }

    private static boolean isFileValid(String path) {
        if (path == null) return false;
        java.io.File f = new java.io.File(path);
        return f.exists() && f.length() > 0;
    }

    private static boolean downloadToFile(String urlStr, String outPath) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) { conn.disconnect(); return false; }
        java.io.File outFile = new java.io.File(outPath);
        java.io.File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.InputStream in = conn.getInputStream();
        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.flush();
        fos.close();
        in.close();
        conn.disconnect();
        return true;
    }

    private static String buildSerialFromUri(String gameUri) {
        // Try to infer PS2 serial from file name: e.g., SLUS-20312 or SLPS_123.45 style
        String last = Uri.parse(gameUri).getLastPathSegment();
        if (last == null) last = "";
        last = last.replace('_', '-');
        // remove extension
        int dot = last.lastIndexOf('.');
        if (dot > 0) last = last.substring(0, dot);
        String serial = null;
        // Very simple heuristic: find token like XXXX-XXXXX
        String upper = last.toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z]{4,5}-[0-9]{3,5})").matcher(upper);
        if (m.find()) {
            serial = m.group(1);
        }
        if (serial == null) {
            serial = upper;
        }
        return serial;
    }

    private static String buildCoverUrlFromSerial(String serial) {
        return "https://raw.githubusercontent.com/izzy2lost/ps2-covers/main/covers/3d/" + serial + ".png";
    }

    private String extractSerialFromUri(String gameUri) {
        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(Uri.parse(gameUri));
            if (in == null) return null;
            // Read first 8MB searching for SYSTEM.CNF contents, e.g., "BOOT2 = cdrom0:\\SLUS_203.12;1"
            final int MAX_BYTES = 8 * 1024 * 1024;
            final byte[] buf = new byte[64 * 1024];
            int read;
            int total = 0;
            StringBuilder sb = new StringBuilder();
            while ((read = in.read(buf)) != -1 && total < MAX_BYTES) {
                total += read;
                // append as ASCII
                sb.append(new String(buf, 0, read));
                // try to match as we go to avoid huge strings
                String found = findSerialInString(sb);
                if (found != null) { in.close(); return found; }
                if (sb.length() > 512 * 1024) sb.delete(0, sb.length() - 128 * 1024); // keep window
            }
            in.close();
        } catch (Exception ignored) { }
        return null;
    }

    private static String findSerialInString(CharSequence cs) {
        // Match common forms: SLUS_203.12, SLPM_650.51, SCES_123.45 etc.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([A-Z]{4,5})[_-]([0-9]{3})\\.([0-9]{2})")
                .matcher(cs);
        if (m.find()) {
            String prefix = m.group(1);
            String part1 = m.group(2);
            String part2 = m.group(3);
            return prefix + "-" + part1 + part2; // SLUS-20312
        }
        return null;
    }

    private static String normalizeSerial(String serial) {
        if (serial == null) return null;
        String s = serial.toUpperCase(Locale.ROOT).replace('_', '-');
        // If form like XXXX-123.45 -> XXXX-12345
        s = s.replaceAll("([A-Z]{4,5})-([0-9]{3})\\.([0-9]{2})", "$1-$2$3");
        return s;
    }

    private void showGameSettings(String gameTitle, String gameUri) {
        // Prefer native extraction so CHDs work
        String gameSerial = null;
        try { gameSerial = NativeApp.getGameSerial(gameUri); } catch (Throwable ignored) {}
        if (gameSerial == null || gameSerial.isEmpty()) {
            gameSerial = extractSerialFromUri(gameUri);
        }
        if (gameSerial == null || gameSerial.isEmpty()) {
            gameSerial = buildSerialFromUri(gameUri);
        }
        gameSerial = normalizeSerial(gameSerial);

        // CRC (native if available)
        String gameCrc = null;
        try { gameCrc = NativeApp.getGameCrc(gameUri); } catch (Throwable ignored) {}
        if (gameCrc == null || gameCrc.isEmpty()) {
            gameCrc = String.format("%08X", Math.abs(gameUri.hashCode()));
        }

        // Debug logging
        android.util.Log.d("GameSettings", "Opening game settings for: " + gameTitle);
        android.util.Log.d("GameSettings", "URI: " + gameUri);
        android.util.Log.d("GameSettings", "Extracted Serial: " + gameSerial);
        android.util.Log.d("GameSettings", "Generated CRC: " + gameCrc);

        GameSettingsDialogFragment dialog = GameSettingsDialogFragment.newInstance(
            gameTitle, gameUri, gameSerial, gameCrc);
        dialog.show(getParentFragmentManager(), "game_settings");
    }
}
