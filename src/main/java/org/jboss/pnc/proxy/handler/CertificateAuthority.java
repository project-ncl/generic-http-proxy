package org.jboss.pnc.proxy.handler;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.proxy.config.ProxyConfiguration;
import org.jboss.pnc.proxy.util.CertUtils;
import org.jboss.pnc.proxy.util.CertificateAndKeys;

@ApplicationScoped
public class CertificateAuthority {

    private final PrivateKey privateKey;

    private final X509Certificate certificate;

    public CertificateAuthority(ProxyConfiguration config) {
        try {
            this.privateKey = CertUtils.getPrivateKey(config.getMITMCAKey());
            this.certificate = CertUtils.loadX509Certificate(new File(config.getMITMCACert()));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create CertificateAuthority", e);
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public CertificateAndKeys createSignedCertificateAndKey(
            String dn,
            boolean isIntermediate) throws Exception {
        return CertUtils.createSignedCertificateAndKey(dn, certificate, privateKey, isIntermediate);
    }
}
