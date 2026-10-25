package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

public final class RoomChooserActivity extends Activity {
    public static final String EXTRA_ADD_MODE = "add_mode";

    private RoomCatalog catalog;
    private final List<Room> filtered = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private Room selected;
    private String campus = SchoolPowerClient.Campus.NORTH;
    private String building = "";
    private boolean addMode;
    private boolean suppressBuildingListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private Spinner campusSpinner;
    private Spinner buildingSpinner;
    private EditText search;
    private ListView list;
    private TextView empty;
    private Button confirm;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        addMode = getIntent().getBooleanExtra(EXTRA_ADD_MODE, false);
        setContentView(R.layout.activity_room_chooser);

        status = findViewById(R.id.chooser_status);
        confirm = findViewById(R.id.button_confirm_room);
        Button cancel = findViewById(R.id.button_cancel_room);
        campusSpinner = findViewById(R.id.spinner_campus);
        buildingSpinner = findViewById(R.id.spinner_building);
        search = findViewById(R.id.input_room_search);
        list = findViewById(R.id.room_list);
        empty = findViewById(R.id.room_empty);
        list.setEmptyView(empty);

        cancel.setVisibility(addMode || !RoomStore.hasSelection(this) ? View.GONE : View.VISIBLE);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });

        ArrayAdapter<String> campusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[] {"北校区", "南校区"});
        campusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        campusSpinner.setAdapter(campusAdapter);
        campusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String nextCampus = position == 1 ? SchoolPowerClient.Campus.SOUTH : SchoolPowerClient.Campus.NORTH;
                if (!nextCampus.equals(campus)) {
                    campus = nextCampus;
                    building = "";
                    selected = null;
                    populateBuildings();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        buildingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressBuildingListener || catalog == null) return;
                List<String> buildings = catalog.buildings(campus);
                building = position >= 0 && position < buildings.size() ? buildings.get(position) : "";
                selected = null;
                updateList();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { scheduleSearch(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < filtered.size()) {
                    Room room = filtered.get(position);
                    if (addMode) {
                        if (selectedIds.contains(room.id)) selectedIds.remove(room.id); else selectedIds.add(room.id);
                    } else {
                        selected = room;
                    }
                    updateList();
                }
            }
        });

        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (addMode) {
                    if (selectedIds.isEmpty()) {
                        Toast.makeText(RoomChooserActivity.this, "请先勾选要添加的寝室", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int added = 0;
                    for (String id : selectedIds) {
                        Room room = catalog == null ? null : catalog.byId(id);
                        if (room != null && RoomStore.addRoom(RoomChooserActivity.this, room)) added++;
                    }
                    RoomStore.setOnboardingDone(RoomChooserActivity.this, true);
                    AppLogger.info("批量添加寝室 " + added + " 个");
                    Toast.makeText(RoomChooserActivity.this, "已添加 " + added + " 个寝室", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                    return;
                }
                if (selected == null) {
                    Toast.makeText(RoomChooserActivity.this, "请先选择寝室", Toast.LENGTH_SHORT).show();
                    return;
                }
                RoomStore.addRoom(RoomChooserActivity.this, selected);
                RoomStore.setCurrent(RoomChooserActivity.this, selected.id);
                AppLogger.info("切换寝室: " + selected.displayName());
                RoomStore.setOnboardingDone(RoomChooserActivity.this, true);
                setResult(RESULT_OK);
                finish();
            }
        });

        status.setText("正在联网拉取寝室列表（最多 5 秒）…");
        CatalogRepository.loadFirstLaunch(this, new CatalogRepository.Callback() {
            @Override public void onLoaded(RoomCatalog loaded, boolean refreshed, String message) {
                if (isFinishing()) return;
                catalog = loaded;
                status.setText(message);
                populateBuildings();
            }
        });

        catalog = CatalogRepository.bundled(this);
        populateBuildings();
    }

    private void populateBuildings() {
        if (catalog == null) return;
        List<String> buildings = catalog.buildings(campus);
        suppressBuildingListener = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, buildings);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        buildingSpinner.setAdapter(adapter);
        int index = buildings.indexOf(building);
        buildingSpinner.setSelection(index >= 0 ? index : 0, false);
        suppressBuildingListener = false;
        int pos = buildingSpinner.getSelectedItemPosition();
        building = pos >= 0 && pos < buildings.size() ? buildings.get(pos) : "";
        selected = null;
        updateList();
    }

    private void scheduleSearch() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        pendingSearch = new Runnable() {
            @Override public void run() { updateList(); }
        };
        handler.postDelayed(pendingSearch, 120L);
    }

    private void updateList() {
        if (catalog == null) return;
        String query = search.getText().toString().trim().toLowerCase();
        List<Room> source = catalog.inBuilding(campus, building);
        filtered.clear();
        for (Room room : source) {
            if (query.isEmpty()
                    || room.name.toLowerCase().contains(query)
                    || room.building.toLowerCase().contains(query)) {
                filtered.add(room);
            }
        }
        List<Map<String, String>> rows = new ArrayList<>(filtered.size());
        for (Room room : filtered) {
            Map<String, String> row = new HashMap<>();
            boolean checked = addMode ? selectedIds.contains(room.id) : (selected != null && selected.id.equals(room.id));
            row.put("name", room.displayName() + (checked ? " ✓" : ""));
            row.put("building", room.campusLabel() + " · " + room.building
                    + (room.floor.isEmpty() ? "" : " · " + room.floor));
            rows.add(row);
        }
        list.setAdapter(new SimpleAdapter(this, rows, R.layout.item_room,
                new String[] {"name", "building"},
                new int[] {R.id.room_item_name, R.id.room_item_building}));
        if (addMode) {
            confirm.setText(selectedIds.isEmpty() ? "勾选并添加寝室" : "添加已勾选的 " + selectedIds.size() + " 个寝室");
        } else {
            confirm.setText(selected == null ? "保存并进入" : "保存 " + selected.displayName() + " 并进入");
        }
    }
}
