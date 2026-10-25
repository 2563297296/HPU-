package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class RoomListActivity extends Activity {
    private static final int REQ_ADD = 7;
    private ListView list;
    private TextView empty;
    private TextView countView;
    private List<Room> rooms = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        setContentView(R.layout.activity_room_list);
        list = findViewById(R.id.room_list_view);
        empty = findViewById(R.id.room_list_empty);
        countView = findViewById(R.id.room_list_count);
        list.setEmptyView(empty);
        findViewById(R.id.room_list_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.room_list_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(RoomListActivity.this, RoomChooserActivity.class);
                intent.putExtra(RoomChooserActivity.EXTRA_ADD_MODE, true);
                startActivityForResult(intent, REQ_ADD);
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < rooms.size()) {
                    Room target = rooms.get(position);
                    RoomStore.setCurrent(RoomListActivity.this, target.id);
                    AppLogger.info("切换寝室: " + target.displayName());
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
        reload();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD) reload();
    }

    private void reload() {
        rooms = RoomStore.rooms(this);
        int count = rooms.size();
        countView.setText("已添加 " + count + " 个寝室 · 点击切换，点 ✕ 删除");
        list.setAdapter(new RoomAdapter());
    }

    private final class RoomAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(RoomListActivity.this);
        @Override public int getCount() { return rooms.size(); }
        @Override public Object getItem(int position) { return rooms.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = inflater.inflate(R.layout.item_room_manage, parent, false);
            final Room room = rooms.get(position);
            TextView name = convertView.findViewById(R.id.manage_room_name);
            TextView sub = convertView.findViewById(R.id.manage_room_sub);
            TextView delete = convertView.findViewById(R.id.manage_room_delete);
            name.setText(room.displayName());
            sub.setText(room.campusLabel() + " · " + room.building
                    + (room.floor.isEmpty() ? "" : " · " + room.floor));
            delete.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    RoomStore.removeRoom(RoomListActivity.this, room.id);
                    Toast.makeText(RoomListActivity.this, "已删除 " + room.displayName(), Toast.LENGTH_SHORT).show();
                    reload();
                }
            });
            return convertView;
        }
    }
}
