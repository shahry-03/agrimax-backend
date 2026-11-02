package com.agrimax.identity.service;

public interface TotpService {
    String generateSecret();
    boolean verifyCode(String secret, String code);
    String generateQrCodeDataUri(String secret, String label, String issuer);
    String generateOtpAuthUrl(String secret, String label, String issuer);
}