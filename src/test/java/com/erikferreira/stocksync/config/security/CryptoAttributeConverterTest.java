package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.config.security.exceptions.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoAttributeConverterTest {

    private CryptoAttributeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CryptoAttributeConverter();
        ReflectionTestUtils.setField(
                converter,
                "encryptionKey",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
    }

    @Test
    void shouldEncryptAndDecryptValue() {
        String encrypted = converter.convertToDatabaseColumn("sensitive-token");
        assertThat(encrypted).isNotEqualTo("sensitive-token");
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo("sensitive-token");
    }

    @Test
    void encryptionShouldUseDifferentIvForEveryCall() {
        assertThat(converter.convertToDatabaseColumn("same-value"))
                .isNotEqualTo(converter.convertToDatabaseColumn("same-value"));
    }

    @Test
    void shouldPreserveNullValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        byte[] encrypted = Base64.getDecoder().decode(converter.convertToDatabaseColumn("secret"));
        encrypted[encrypted.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(encrypted);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(CryptoException.class);
    }
}
