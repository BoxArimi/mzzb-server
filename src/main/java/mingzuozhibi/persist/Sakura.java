package mingzuozhibi.persist;

import org.json.JSONObject;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.support.Constants.SakuraTop100Key;
import static mingzuozhibi.support.Constants.SakuraTop100Title;

@Entity
public class Sakura extends BaseModel implements Comparable<Sakura> {

    public enum ViewType {
        SakuraList, PublicList, PrivateList
    }

    private String key;
    private String title;
    private boolean enabled;
    private ViewType viewType;
    private LocalDateTime modifyTime;
    private List<Disc> discs = new LinkedList<>();

    public Sakura() {
    }

    public Sakura(String key, String title, ViewType viewType) {
        this.key = key;
        this.enabled = true;
        this.viewType = viewType;
        this.title = title == null ? titleOfKey(key) : title;
    }

    @Column(name = "`key`", length = 100, nullable = false, unique = true)
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Column(length = 100, nullable = false)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Column(nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Column(nullable = false)
    public ViewType getViewType() {
        return viewType;
    }

    public void setViewType(ViewType viewType) {
        this.viewType = viewType;
    }

    @Column
    public LocalDateTime getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime = modifyTime;
    }

    @ManyToMany
    @JoinTable(name = "sakura_discs",
            joinColumns = {@JoinColumn(name = "sakura_id")},
            inverseJoinColumns = {@JoinColumn(name = "disc_id")})
    public List<Disc> getDiscs() {
        return discs;
    }

    public void setDiscs(List<Disc> discs) {
        this.discs = discs;
    }

    @Transient
    public boolean isTop100() {
        return SakuraTop100Key.equals(getKey());
    }

    @Override
    public int compareTo(Sakura o) {
        Objects.requireNonNull(o);
        return Comparator.comparing(Sakura::getKey).compare(this, o);
    }

    public JSONObject toJSON() {
        JSONObject object = new JSONObject();
        object.put("id", getId());
        object.put("key", getKey());
        object.put("title", getTitle());
        object.put("enabled", isEnabled());
        object.put("viewType", getViewType());
        object.put("modifyTime", toEpochMilli(modifyTime));
        return object;
    }

    private static String titleOfKey(String key) {
        Objects.requireNonNull(key);
        if (key.equals(SakuraTop100Key)) {
            return SakuraTop100Title;
        }
        Matcher matcher = Pattern.compile("^(\\d{4})-(\\d{2})$").matcher(key);
        if (matcher.find()) {
            return String.format("%s年%s月新番", matcher.group(1), matcher.group(2));
        }
        return "未命名列表";
    }

}
