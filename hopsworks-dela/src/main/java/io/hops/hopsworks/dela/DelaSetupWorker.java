/*
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.hops.hopsworks.dela;

import com.google.gson.Gson;
import io.hops.hopsworks.common.dao.dela.certs.ClusterCertificateFacade;
import io.hops.hopsworks.common.dela.AddressJSON;
import io.hops.hopsworks.common.security.CertificatesMgmService;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.dela.dto.hopssite.ClusterServiceDTO;
import io.hops.hopsworks.dela.exception.ThirdPartyException;
import io.hops.hopsworks.dela.hopssite.HopssiteController;
import io.hops.hopsworks.util.CertificateHelper;
import io.hops.hopsworks.util.SettingsHelper;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import org.apache.commons.codec.digest.DigestUtils;
import org.javatuples.Pair;
import org.javatuples.Triplet;

@Startup
@Singleton
public class DelaSetupWorker {

  private final static Logger LOG = Logger.getLogger(DelaSetupWorker.class.getName());

  @Resource
  TimerService timerService;
  @EJB
  private Settings settings;
  @EJB
  private ClusterCertificateFacade clusterCertFacade;
  @EJB
  private DelaStateController delaStateCtrl;
  @EJB
  private HopssiteController hopsSiteProxy;
  @EJB
  private TransferDelaController delaCtrl;
  @EJB
  private CertificatesMgmService certificatesMgmService;

  private State state;
  //5 required to get from start to running in perfect mode
  private int tryTimeouts = 0;
  private boolean healthy = true;
  /**
   * after 20 tries without reaching last state(active - running), backoff and try a bit less often, there is something
   * obviously wrong with some other third party service. .Give it time to regenerate
   */
  private static int TRY_TIMERS_BACKOFF = 20;

  @PostConstruct
  private void init() {
    if (delaStateCtrl.delaEnabled()) {
      state = State.SETUP;
      timerService.createTimer(0, settings.getHOPSSITE_HEARTBEAT_RETRY(), "Timer for dela settings check.");
      LOG.log(Level.INFO, "{0} - state:{1}", new Object[]{ThirdPartyException.Source.HOPS_SITE, state});
    }
  }

  @PreDestroy
  private void destroyTimer() {
    for (Timer timer : timerService.getTimers()) {
      timer.cancel();
    }
  }

  private Timer resetTimer(Timer timer, long intervalDuration) {
    timer.cancel();
    return timerService.createTimer(intervalDuration, intervalDuration, "Timer for " + state + ".");
  }

  @Timeout
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  private void timeout(Timer timer) {
    LOG.log(Level.INFO, "{0} - state:{1} timeout:{2}",
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, timer.getInfo().toString()});
    switch (state) {
      case SETUP:
        setup(timer);
        break;
      case DELA_VERSION:
        delaVersion(timer);
        break;
      case DELA_CONTACT:
        delaContact(timer);
        break;
      case REGISTER:
        hopsSiteRegister(timer);
        break;
      case HEAVY_PING:
        heavyPing(timer);
        break;
      case PING:
        ping(timer);
        break;
      default:
        throw new IllegalStateException("unknown state");
    }
  }

  //********************************************************************************************************************
  private void setup(Timer timer) {
    Optional<String> masterPswd = settings.getHopsSiteClusterPswd();
    if (!masterPswd.isPresent()) {
      //TODO Alex - use the registration pswd hash once the admin UI is ready
      String pswd = DigestUtils.sha256Hex(settings.getHopsSiteClusterPswdAux());
      settings.setHopsSiteClusterPswd(pswd);
      masterPswd = settings.getHopsSiteClusterPswd();
    }
    Optional<String> clusterName = settings.getHopsSiteClusterName();

    if (clusterName.isPresent()) {
      Optional<Triplet<KeyStore, KeyStore, String>> keystoreAux
        = CertificateHelper.loadKeystoreFromDB(masterPswd.get(), clusterName.get(), clusterCertFacade,
          certificatesMgmService);
      if (keystoreAux.isPresent()) {
        setupComplete(keystoreAux.get(), timer);
        return;
      }
    }

    Optional<Triplet<KeyStore, KeyStore, String>> keystoreAux
      = CertificateHelper.loadKeystoreFromFile(masterPswd.get(), settings, clusterCertFacade, certificatesMgmService);
    if (keystoreAux.isPresent()) {
      setupComplete(keystoreAux.get(), timer);
    } else {
      LOG.log(Level.WARNING, "{0} - dela setup not ready - certificates not ready",
        new Object[]{ThirdPartyException.Source.HOPS_SITE});
    }
  }

  private void setupComplete(Triplet<KeyStore, KeyStore, String> keystoreAux, Timer timer) {
    KeyStore keystore = keystoreAux.getValue0();
    KeyStore truststore = keystoreAux.getValue1();
    String certPswd = keystoreAux.getValue2();
    delaStateCtrl.hopssiteCertsAvailable(keystore, truststore, certPswd);
    timer = resetToDelaVersion(timer);
    delaVersion(timer);
  }

  private String delaVersion;

  private void delaVersion(Timer timer) {
    LOG.log(Level.INFO, "{0} - retrieving hops-site dela_version", new Object[]{ThirdPartyException.Source.HOPS_SITE});
    try {
      delaVersion = hopsSiteProxy.delaVersion();
      delaStateCtrl.hopssiteContacted();
      timer = resetToDelaContact(timer);
      delaContact(timer);
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      timer = tryAgainLater(timer);
    }
  }

  private void delaContact(Timer timer) {
    LOG.log(Level.INFO, "{0} - state:{1}", new Object[]{ThirdPartyException.Source.HOPS_SITE, state});
    AddressJSON delaTransferEndpoint;
    try {
      delaTransferEndpoint = SettingsHelper.delaTransferEndpoint(settings);
    } catch (ThirdPartyException tpe) {
      try {
        delaTransferEndpoint = getDelaTransferEndpoint(delaVersion);
        delaStateCtrl.transferDelaContacted();
        settings.setDELA_PUBLIC_ENDPOINT(delaTransferEndpoint);
        timer = resetToRegister(timer);
        hopsSiteRegister(timer, delaTransferEndpoint);
      } catch (ThirdPartyException tpe2) {
        LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}", new Object[]{ThirdPartyException.Source.HOPS_SITE,
          tpe2.getSource(), tpe2.getSourceDetails(), tpe2.getMessage()});
        timer = tryAgainLater(timer);
      }
    }

  }

  private void hopsSiteRegister(Timer timer) {
    LOG.log(Level.INFO, "{0} - state:{1}", new Object[]{ThirdPartyException.Source.HOPS_SITE, state});
    AddressJSON delaTransferEndpoint;
    try {
      delaTransferEndpoint = SettingsHelper.delaTransferEndpoint(settings);
    } catch (ThirdPartyException ex) {
      timer = resetToDelaContact(timer);
      return;
    }
    try {
      hopsSiteRegister(timer, delaTransferEndpoint);
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      timer = tryAgainLater(timer);
    }
  }

  private void hopsSiteRegister(Timer timer, AddressJSON delaTransferEndpoint) throws ThirdPartyException {
    String delaHttpEndpoint = SettingsHelper.delaHttpEndpoint(settings);
    hopsSiteRegister(timer, delaHttpEndpoint, delaTransferEndpoint);
  }

  private void hopsSiteRegister(Timer timer, String delaClusterAddress, AddressJSON delaTransferAddress)
    throws ThirdPartyException {
    String publicCId = hopsSiteProxy.registerCluster(delaClusterAddress, new Gson().toJson(delaTransferAddress));
    settings.setDELA_CLUSTER_ID(publicCId);
    timer = resetToHeavyPing(timer);
    heavyPing(timer);
  }

  private void heavyPing(Timer timer) {
    LOG.log(Level.INFO, "{0} - state:{1}", new Object[]{ThirdPartyException.Source.HOPS_SITE, state});
    Pair<List<String>, List<String>> datasets;
    try {
      datasets = delaCtrl.getContents();
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      if (ThirdPartyException.Source.SETTINGS.equals(tpe.getSource())) {
        timer = resetToDelaContact(timer);
      } else {
        timer = tryAgainLater(timer);
      }
      return;
    }
    try {
      hopsSiteProxy.heavyPing(datasets.getValue0(), datasets.getValue1());
      timer = resetToPing(timer);
      ping(timer);
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{2}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      if (ThirdPartyException.Error.CLUSTER_NOT_REGISTERED.is(tpe.getMessage())) {
        timer = resetToRegister(timer);
      } else if (ThirdPartyException.Source.SETTINGS.equals(tpe.getSource())) {
        timer = resetToRegister(timer);
      } else {
        timer = tryAgainLater(timer);
      }
    }
  }

  private void ping(Timer timer) {
    LOG.log(Level.INFO, "{0} - state:{1}", new Object[]{ThirdPartyException.Source.HOPS_SITE, state});
    Pair<List<String>, List<String>> datasets;
    try {
      datasets = delaCtrl.getContents();
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      if (ThirdPartyException.Source.SETTINGS.equals(tpe.getSource())) {
        timer = resetToDelaContact(timer);
      } else {
        timer = tryAgainLater(timer);
      }
      return;
    }
    try {
      hopsSiteProxy.ping(new ClusterServiceDTO.Ping(datasets.getValue0().size(), datasets.getValue1().size()));
      if (!healthy) {
        timer = healthyPing(timer);
      }
    } catch (ThirdPartyException tpe) {
      LOG.log(Level.WARNING, "{0} - source:<{1}:{2}>:{3}",
        new Object[]{ThirdPartyException.Source.HOPS_SITE, tpe.getSource(), tpe.getSourceDetails(), tpe.getMessage()});
      if (ThirdPartyException.Error.CLUSTER_NOT_REGISTERED.is(tpe.getMessage())) {
        timer = resetToRegister(timer);
      } else if (ThirdPartyException.Error.HEAVY_PING.is(tpe.getMessage())) {
        timer = resetToHeavyPing(timer);
      } else if (ThirdPartyException.Source.SETTINGS.equals(tpe.getSource())) {
        timer = resetToDelaContact(timer);
      } else {
        timer = tryAgainLater(timer);
      }
    }
  }

  private Timer tryTimer(Timer timer, boolean health) {
    this.healthy = health;
    long intervalDuration;
    if (this.healthy) {
      tryTimeouts = 0;
      intervalDuration = settings.getHOPSSITE_HEARTBEAT_INTERVAL();
    } else {
      tryTimeouts++;
      if (tryTimeouts < TRY_TIMERS_BACKOFF) {
        intervalDuration = settings.getHOPSSITE_HEARTBEAT_RETRY();
      } else {
        //backoff and try again after a longer timeout
        intervalDuration = settings.getHOPSSITE_HEARTBEAT_INTERVAL();
      }
    }
    return resetTimer(timer, intervalDuration);
  }

  private Timer tryAgainLater(Timer timer) {
    return tryTimer(timer, false);
  }

  private Timer resetToSettings(Timer timer) {
    state = State.SETUP;
    return tryTimer(timer, false);
  }

  private Timer resetToDelaVersion(Timer timer) {
    LOG.log(Level.WARNING, "{0} - reset from:{1} to {2}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, State.DELA_VERSION});
    state = State.DELA_VERSION;
    return tryTimer(timer, false);
  }

  private Timer resetToDelaContact(Timer timer) {
    LOG.log(Level.WARNING, "{0} - reset from:{1} to {2}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, State.DELA_CONTACT});
    state = State.DELA_CONTACT;
    return tryTimer(timer, false);
  }

  private Timer resetToRegister(Timer timer) {
    LOG.log(Level.WARNING, "{0} - reset from:{1} to {2}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, State.REGISTER});
    state = State.REGISTER;
    return tryTimer(timer, false);
  }

  private Timer resetToHeavyPing(Timer timer) {
    LOG.log(Level.WARNING, "{0} - reset from:{1} to {2}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, State.HEAVY_PING});
    state = State.HEAVY_PING;
    return tryTimer(timer, false);
  }

  private Timer resetToPing(Timer timer) {
    LOG.log(Level.WARNING, "{0} - reset from:{1} to {2}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, state, State.PING});
    state = State.PING;
    return tryTimer(timer, false);
  }

  private Timer healthyPing(Timer timer) {
    state = State.PING;
    return tryTimer(timer, true);
  }

  private String getDelaTransferHttpEndpoint() throws HeartbeatException {
    String delaHttpEndpoint = settings.getDELA_TRANSFER_HTTP_ENDPOINT();
    try {
      URL u = new URL(delaHttpEndpoint);
      u.toURI();
      // validate url syntax
      if (u.getHost().isEmpty() || u.getPort() == -1) {
        LOG.log(Level.WARNING, "{0} - malformed dela url. {1} - reset in database", 
          new Object[]{ThirdPartyException.Source.HOPS_SITE, delaHttpEndpoint});
        throw new HeartbeatException("DELA_TRANSFER_HTTP");
      }
      return delaHttpEndpoint;
    } catch (MalformedURLException | URISyntaxException ex) {
      LOG.log(Level.SEVERE, "{1} - malformed dela url. {1} - reset in database", 
        new Object[]{ThirdPartyException.Source.HOPS_SITE, delaHttpEndpoint});
      throw new HeartbeatException("DELA_TRANSFER_HTTP");
    }
  }

  private AddressJSON getDelaTransferEndpoint(String delaVersion) throws ThirdPartyException {
    String delaTransferHttpEndpoint = SettingsHelper.delaTransferHttpEndpoint(settings);
    LOG.log(Level.INFO, "{0} - dela http endpoint: {1}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, delaTransferHttpEndpoint});
    AddressJSON delaTransferEndpoint = delaCtrl.getDelaPublicEndpoint(delaVersion);
    LOG.log(Level.INFO, "{0} - dela transfer endpoint: {1}", 
      new Object[]{ThirdPartyException.Source.HOPS_SITE, delaTransferEndpoint.toString()});
    return delaTransferEndpoint;

  }

  private static class HeartbeatException extends Exception {

    public HeartbeatException(String msg) {
      super(msg);
    }
  }

  private static enum State {

    SETUP,
    DELA_VERSION,
    DELA_CONTACT,
    REGISTER,
    HEAVY_PING,
    PING
  }
}
