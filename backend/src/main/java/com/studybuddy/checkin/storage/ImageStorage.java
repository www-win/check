package com.studybuddy.checkin.storage;

/** 图片存储抽象。默认实现为阿里云 OSS，可替换为 COS 等。 */
public interface ImageStorage {

    /**
     * 上传图片，返回可公网访问的 URL。
     *
     * @param userId 上传者，用于组织对象键
     * @param data   图片字节
     * @param ext    扩展名（不含点），如 jpg/png/webp
     */
    String upload(Long userId, byte[] data, String ext);
}
