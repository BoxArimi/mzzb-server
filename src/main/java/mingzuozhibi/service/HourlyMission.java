package mingzuozhibi.service;

import mingzuozhibi.persist.core.AutoLogin;
import mingzuozhibi.persist.disc.Disc;
import mingzuozhibi.persist.disc.Disc.UpdateType;
import mingzuozhibi.persist.disc.Record;
import mingzuozhibi.persist.disc.Sakura;
import mingzuozhibi.support.Dao;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static mingzuozhibi.persist.disc.Sakura.ViewType.SakuraList;

@Service
public class HourlyMission {

    @Autowired
    private Dao dao;
    public static final Logger LOGGER = LoggerFactory.getLogger(HourlyMission.class);

    public void removeExpiredAutoLoginData() {
        dao.execute(session -> {
            @SuppressWarnings("unchecked")
            List<AutoLogin> expired = session.createCriteria(AutoLogin.class)
                    .add(Restrictions.lt("expired", LocalDateTime.now()))
                    .list();

            expired.forEach(autoLogin -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[定时任务][移除过期的AutoLogin数据][id={}, username={}]",
                            autoLogin.getId(), autoLogin.getUser().getUsername());
                }
                dao.delete(autoLogin);
            });
        });
    }

    public void removeExpiredDiscsFromList() {
        dao.execute(session -> {
            @SuppressWarnings("unchecked")
            List<Sakura> sakuras = session.createCriteria(Sakura.class)
                    .add(Restrictions.ne("key", "9999-99"))
                    .add(Restrictions.eq("enabled", true))
                    .add(Restrictions.eq("viewType", SakuraList))
                    .list();

            sakuras.forEach(sakura -> {
                List<Disc> toDelete = sakura.getDiscs().stream()
                        .filter(this::notSakuraUpdateType)
                        .filter(this::isReleasedSevenDays)
                        .collect(Collectors.toList());
                sakura.getDiscs().removeAll(toDelete);

                if (sakura.getDiscs().isEmpty()) {
                    sakura.setEnabled(false);
                }

                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("[定时任务][移除过期的Sakura碟片][sakura={}, delete={}]",
                            sakura.getTitle(), toDelete.size());
                    toDelete.forEach(disc -> LOGGER.info("[移除碟片][sakura={}, disc={}]",
                            sakura.getTitle(), disc.getTitle()));
                    if (!sakura.isEnabled() && toDelete.size() > 0) {
                        LOGGER.info("[Sakura列表为空: setEnabled(false)]");
                    }
                }
            });
        });
    }


    public void recordNotSakuraDiscsRank() {
        LocalDateTime japanTime = LocalDateTime.now().plusHours(1);
        LocalDate date = japanTime.toLocalDate();
        int hour = japanTime.getHour();
        dao.execute(session -> {
            @SuppressWarnings("unchecked")
            List<Disc> discs = session.createCriteria(Disc.class)
                    .add(Restrictions.ne("updateType", UpdateType.Sakura))
                    .add(Restrictions.ne("updateType", UpdateType.None))
                    .add(Restrictions.gt("releaseDate", date.minusDays(7)))
                    .list();

            LOGGER.info("[定时任务][记录非Sakura碟片排名][碟片数量为:{}]", discs.size());

            discs.forEach(disc -> {
                Record record = (Record) session.createCriteria(Record.class)
                        .add(Restrictions.eq("disc", disc))
                        .add(Restrictions.eq("date", date))
                        .uniqueResult();
                if (record == null) {
                    record = new Record(disc, date);
                    dao.save(record);
                }
                record.setRank(hour, disc.getThisRank());
            });

            LOGGER.info("[定时任务][计算非Sakura碟片PT][碟片数量为:{}]", discs.size());

            discs.forEach(disc -> {
                @SuppressWarnings("unchecked")
                List<Record> records = session.createCriteria(Record.class)
                        .add(Restrictions.eq("disc", disc))
                        .add(Restrictions.lt("date", disc.getReleaseDate()))
                        .addOrder(Order.asc("date"))
                        .list();
                disc.setTotalPt((int) computeTotalPt(disc, records));
            });
        });
    }

    private double computeTotalPt(Disc disc, List<Record> records) {
        AtomicReference<Integer> lastRank = new AtomicReference<>();
        return records.stream()
                .mapToDouble(record -> computeRecordPt(disc, record, lastRank))
                .sum();
    }

    private double computeRecordPt(Disc disc, Record record, AtomicReference<Integer> lastRank) {
        double recordPt = 0d;
        for (int i = 0; i < 24; i++) {
            Integer rank = record.getRank(i + 1);
            if (rank == null) {
                rank = lastRank.get();
            } else {
                lastRank.set(rank);
            }
            if (rank != null) {
                recordPt += computeHourPt(disc, rank);
            }
        }
        return recordPt;
    }

    private double computeHourPt(Disc disc, Integer rank) {
        switch (disc.getDiscType()) {
            case Cd:
                return computePt(150, 5.25, rank);
            case Other:
                return 0d;
            default:
                if (disc.getTitle().contains("Blu-ray")) {
                    if (rank <= 10) {
                        return computePt(100, 3.2, rank);
                    } else if (rank <= 20) {
                        return computePt(100, 3.3, rank);
                    } else if (rank <= 50) {
                        return computePt(100, 3.4, rank);
                    } else if (rank <= 100) {
                        return computePt(100, 3.6, rank);
                    } else if (rank <= 300) {
                        return computePt(100, 3.8, rank);
                    } else {
                        return computePt(100, 3.9, rank);
                    }
                } else {
                    return computePt(100, 4.2, rank);
                }
        }
    }

    private static double computePt(int div, double base, int rank) {
        return div / Math.exp(Math.log(rank) / Math.log(base));
    }

    private boolean isReleasedSevenDays(Disc disc) {
        LocalDate releaseTenDays = disc.getReleaseDate().plusDays(7);
        return LocalDate.now().isAfter(releaseTenDays);
    }

    private boolean notSakuraUpdateType(Disc disc) {
        return disc.getUpdateType() != UpdateType.Sakura;
    }

}
