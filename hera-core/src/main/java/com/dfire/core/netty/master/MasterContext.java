package com.dfire.core.netty.master;

import com.dfire.common.service.*;
import com.dfire.common.vo.HeraHostGroupVo;
import com.dfire.core.config.HeraGlobalEnvironment;
import com.dfire.core.event.Dispatcher;
import com.dfire.core.quartz.QuartzSchedulerService;
import com.dfire.core.queue.JobElement;
import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * @author: <a href="mailto:lingxiao@2dfire.com">凌霄</a>
 * @time: Created in 14:10 2018/1/12
 * @desc hera调度器执行上下文
 */

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MasterContext {

    private QuartzSchedulerService quartzSchedulerService;
    private Master master;

    private Map<Channel, MasterWorkHolder> workMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;


    private Dispatcher dispatcher;
    private Map<Integer, HeraHostGroupVo> hostGroupCache;
    /**
     * @desc 1. quartz发生任务调度的时候，任务会先进入到exceptionQueue队列，等待被扫描调度，随后进入调度队列
     * 2. 手动执行任务，manualQueue，等待被扫描调度，随后进入调度队列
     * 3. debugQueue，任务会先进入到exceptionQueue队列，等待被扫描调度，随后进入调度队列
     */
    private Queue<JobElement> scheduleQueue = new PriorityBlockingQueue(10000, new Comparator<JobElement>() {
        @Override
        public int compare(JobElement element1, JobElement element2) {
            int p1 = element1.getPriorityLevel();
            int p2 = element1.getPriorityLevel();
            return p1 == p2 ? 0 : (p1 > p2 ? 1 : -1);
        }
    });
    private Queue<JobElement> exceptionQueue = new LinkedBlockingQueue<>();
    private Queue<JobElement> debugQueue = new ArrayBlockingQueue<>(1000);
    private Queue<JobElement> manualQueue = new ArrayBlockingQueue<>(1000);

    private MasterHandler handler;
    private MasterServer masterServer;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    /**
     * 后面成可配置的
     */

    final Timer masterTimer = new HashedWheelTimer(Executors.defaultThreadFactory(), 5, TimeUnit.SECONDS);

    public MasterContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void init() {
        dispatcher = new Dispatcher();
        handler = new MasterHandler(this);
        masterServer = new MasterServer(handler);
        masterServer.start(HeraGlobalEnvironment.getConnectPort());
        master = new Master(this);
        log.info("end init master content success ");
    }

    public void destroy() {
        threadPool.shutdown();
        masterTimer.stop();
        if (masterServer != null) {
            masterServer.shutdown();
        }
        if (quartzSchedulerService != null) {
            try {
                quartzSchedulerService.shutdown();
                log.info("quartz schedule shutdown success");
            } catch (Exception e) {
                e.printStackTrace();
                log.info("quartz schedule shutdown error");
            }
        }
        log.info("destroy master context success");
    }

    public synchronized Map<Integer, HeraHostGroupVo> getHostGroupCache() {
        return hostGroupCache;
    }

    public HeraHostGroupService getHeraHostGroupService() {
        return (HeraHostGroupService) applicationContext.getBean("heraHostGroupService");
    }

    public HeraHostRelationService getHeraHostRelationService() {
        return (HeraHostRelationService) applicationContext.getBean("heraHostRelationService");
    }

    public HeraFileService getHeraFileService() {
        return (HeraFileService) applicationContext.getBean("heraFileService");
    }

    public HeraProfileService getHeraProfileService() {
        return (HeraProfileService) applicationContext.getBean("heraProfileService");
    }

    public QuartzSchedulerService getQuartzSchedulerService() {
        return quartzSchedulerService;
    }

    public HeraGroupService getHeraGroupService() {
        return (HeraGroupService) applicationContext.getBean("heraGroupService");
    }

    public HeraJobHistoryService getHeraJobHistoryService() {
        return (HeraJobHistoryService) applicationContext.getBean("heraJobHistoryService");
    }

    public HeraUserService getHeraUserService() {
        return (HeraUserService) applicationContext.getBean("heraUserService");
    }

    public HeraJobService getHeraJobService() {
        return (HeraJobService) applicationContext.getBean("heraJobService");
    }

    public HeraDebugHistoryService getHeraDebugHistoryService() {
        return (HeraDebugHistoryService) applicationContext.getBean("heraDebugHistoryService");
    }

    public HeraJobActionService getHeraJobActionService() {
        return (HeraJobActionService) applicationContext.getBean("heraJobActionService");
    }

    public synchronized void refreshHostGroupCache() {
        try {
            hostGroupCache = getHeraHostGroupService().getAllHostGroupInfo();
        } catch (Exception e) {
            log.info("refresh host group error");
        }
    }
}
