package org.jasig.portal.aggr;

import org.jasig.portal.concurrency.FunctionWithoutResult;
import org.jasig.portal.concurrency.locking.ClusterMutex;
import org.jasig.portal.concurrency.locking.IClusterLockService;
import org.jasig.portal.concurrency.locking.LockOptions;
import org.jasig.portal.concurrency.locking.IClusterLockService.LockStatus;
import org.jasig.portal.concurrency.locking.IClusterLockService.TryLockFunctionResult;
import org.jasig.portal.portlet.dao.IMarketplaceRatingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("portletRatingAggregator")
public class PortletRatingAggregatorImpl implements PortletRatingAggregator, DisposableBean {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String PORTLET_RATING_AGGREGATOR_LOCK_NAME = PortletRatingAggregatorImpl.class.getName() + ".PURGE_LOCK";

    private IClusterLockService clusterLockService;
    private IMarketplaceRatingDao marketplaceRatingDao;
    private volatile boolean shutdown = false;
    
    @Override
    public void destroy() throws Exception {
        this.shutdown = true;
    }
    
    @Autowired
    public void setClusterLockService(IClusterLockService clusterLockService) {
        this.clusterLockService = clusterLockService;
    }
    
    @Autowired
    public void setMarketplaceRatingDao(IMarketplaceRatingDao dao) {
        this.marketplaceRatingDao = dao;
    }
    
    @Override
    public boolean aggregatePortletRatings() {
        
        if (shutdown) {
            logger.warn("aggregatePortletRatings called after shutdown, ignoring call");
            return false;
        }
        
        try {
            final TryLockFunctionResult<Object> result = this.clusterLockService.doInTryLock(
                PORTLET_RATING_AGGREGATOR_LOCK_NAME, 
                LockOptions.builder().lastRunDelay(0), 
                new FunctionWithoutResult<ClusterMutex>() {
                    @Override
                    protected void applyWithoutResult(ClusterMutex input) {
                        marketplaceRatingDao.aggregateMarketplaceRating();
                    }
                }
            );
            return result.getLockStatus() ==  LockStatus.EXECUTED;
        }
        catch (InterruptedException e) {
            logger.warn("Interrupted while aggregating portlet ratings", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
