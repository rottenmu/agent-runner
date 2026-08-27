package com.zimo.module.ai.skillimport;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 单技能 ZIP 的内存结构校验器。
 *
 * <p>校验器在解压前核对 EOCD、中央目录和本地条目元数据，避免为判断非法包结构而解压额外条目。
 * 仅支持单磁盘、非 ZIP64、未加密且只包含根目录 {@code skill.json} 的标准 ZIP。
 */
final class AiSkillZipStructureValidator {

    private static final int MAX_ENTRY_COUNT = 16;
    private static final int EOCD_MIN_SIZE = 22;
    private static final int MAX_COMMENT_SIZE = 0xFFFF;
    private static final int CENTRAL_HEADER_SIZE = 46;
    private static final int LOCAL_HEADER_SIZE = 30;
    private static final long EOCD_SIGNATURE = 0x06054B50L;
    private static final long CENTRAL_SIGNATURE = 0x02014B50L;
    private static final long LOCAL_SIGNATURE = 0x04034B50L;
    private static final long DATA_DESCRIPTOR_SIGNATURE = 0x08074B50L;
    private static final long ZIP64_MARKER = 0xFFFFFFFFL;
    private static final int ZIP64_ENTRY_COUNT = 0xFFFF;
    private static final int DATA_DESCRIPTOR_FLAG = 1 << 3;
    private static final int ENCRYPTED_FLAG = 1;
    private static final int STRONG_ENCRYPTION_FLAG = 1 << 6;
    private static final byte[] MANIFEST_NAME = "skill.json".getBytes(StandardCharsets.US_ASCII);
    private static final String INVALID_ARCHIVE_MESSAGE = "ZIP 只能包含根目录 skill.json";
    private static final String CORRUPT_ARCHIVE_MESSAGE = "ZIP 文件损坏或不受支持";

    void validate(byte[] archive) {
        Eocd eocd = readEocd(archive);
        if (eocd.entryCount() > MAX_ENTRY_COUNT) {
            throw AiSkillImportException.tooLarge("ZIP 条目数不能超过 16");
        }
        CentralEntry[] entries = readCentralDirectory(archive, eocd);
        if (entries.length != 1 || !Arrays.equals(entries[0].name(), MANIFEST_NAME)) {
            throw invalidArchive();
        }
        validateLocalEntry(archive, entries[0], eocd.centralOffset());
    }

    private Eocd readEocd(byte[] archive) {
        int offset = findEocdOffset(archive);
        int diskNumber = unsignedShort(archive, offset + 4);
        int centralDisk = unsignedShort(archive, offset + 6);
        int entriesOnDisk = unsignedShort(archive, offset + 8);
        int entryCount = unsignedShort(archive, offset + 10);
        long centralSize = unsignedInt(archive, offset + 12);
        long centralOffset = unsignedInt(archive, offset + 16);
        if (diskNumber != 0 || centralDisk != 0 || entriesOnDisk != entryCount) {
            throw corruptArchive();
        }
        if (entryCount == ZIP64_ENTRY_COUNT
                || centralSize == ZIP64_MARKER
                || centralOffset == ZIP64_MARKER) {
            throw corruptArchive();
        }
        requireRange(archive, centralOffset, centralSize);
        if (centralOffset + centralSize != offset) {
            throw corruptArchive();
        }
        return new Eocd(entryCount, centralOffset, centralSize);
    }

    private int findEocdOffset(byte[] archive) {
        if (archive.length < EOCD_MIN_SIZE) {
            throw corruptArchive();
        }
        int firstCandidate = archive.length - EOCD_MIN_SIZE;
        int lowerBound = Math.max(0, firstCandidate - MAX_COMMENT_SIZE);
        for (int offset = firstCandidate; offset >= lowerBound; offset--) {
            if (unsignedInt(archive, offset) != EOCD_SIGNATURE) {
                continue;
            }
            int commentSize = unsignedShort(archive, offset + 20);
            if (offset + EOCD_MIN_SIZE + commentSize == archive.length) {
                return offset;
            }
        }
        throw corruptArchive();
    }

    private CentralEntry[] readCentralDirectory(byte[] archive, Eocd eocd) {
        CentralEntry[] entries = new CentralEntry[eocd.entryCount()];
        long cursor = eocd.centralOffset();
        long centralEnd = eocd.centralOffset() + eocd.centralSize();
        for (int index = 0; index < entries.length; index++) {
            entries[index] = readCentralEntry(archive, cursor, centralEnd);
            cursor = entries[index].nextCentralOffset();
        }
        if (cursor != centralEnd) {
            throw corruptArchive();
        }
        return entries;
    }

    private CentralEntry readCentralEntry(byte[] archive, long offset, long centralEnd) {
        requireRange(archive, offset, CENTRAL_HEADER_SIZE);
        int cursor = Math.toIntExact(offset);
        if (unsignedInt(archive, cursor) != CENTRAL_SIGNATURE) {
            throw corruptArchive();
        }
        int flags = unsignedShort(archive, cursor + 8);
        int method = unsignedShort(archive, cursor + 10);
        long crc = unsignedInt(archive, cursor + 16);
        long compressedSize = unsignedInt(archive, cursor + 20);
        long uncompressedSize = unsignedInt(archive, cursor + 24);
        int nameLength = unsignedShort(archive, cursor + 28);
        int extraLength = unsignedShort(archive, cursor + 30);
        int commentLength = unsignedShort(archive, cursor + 32);
        int startDisk = unsignedShort(archive, cursor + 34);
        long localOffset = unsignedInt(archive, cursor + 42);
        rejectUnsupportedEntry(flags, method, compressedSize, uncompressedSize, localOffset, startDisk);
        long nextOffset = offset + CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength;
        if (nextOffset > centralEnd) {
            throw corruptArchive();
        }
        requireRange(archive, offset + CENTRAL_HEADER_SIZE, nameLength);
        byte[] name = Arrays.copyOfRange(
                archive, cursor + CENTRAL_HEADER_SIZE, cursor + CENTRAL_HEADER_SIZE + nameLength);
        return new CentralEntry(
                name, flags, method, crc, compressedSize, uncompressedSize, localOffset, nextOffset);
    }

    private void rejectUnsupportedEntry(
            int flags,
            int method,
            long compressedSize,
            long uncompressedSize,
            long localOffset,
            int startDisk) {
        if ((flags & (ENCRYPTED_FLAG | STRONG_ENCRYPTION_FLAG)) != 0
                || (method != 0 && method != 8)
                || compressedSize == ZIP64_MARKER
                || uncompressedSize == ZIP64_MARKER
                || localOffset == ZIP64_MARKER
                || startDisk != 0) {
            throw corruptArchive();
        }
        if (method == 0 && compressedSize != uncompressedSize) {
            throw corruptArchive();
        }
    }

    private void validateLocalEntry(byte[] archive, CentralEntry entry, long centralOffset) {
        if (entry.localOffset() != 0) {
            throw corruptArchive();
        }
        requireRange(archive, entry.localOffset(), LOCAL_HEADER_SIZE);
        int offset = Math.toIntExact(entry.localOffset());
        if (unsignedInt(archive, offset) != LOCAL_SIGNATURE) {
            throw corruptArchive();
        }
        int flags = unsignedShort(archive, offset + 6);
        int method = unsignedShort(archive, offset + 8);
        long crc = unsignedInt(archive, offset + 14);
        long compressedSize = unsignedInt(archive, offset + 18);
        long uncompressedSize = unsignedInt(archive, offset + 22);
        int nameLength = unsignedShort(archive, offset + 26);
        int extraLength = unsignedShort(archive, offset + 28);
        long nameOffset = entry.localOffset() + LOCAL_HEADER_SIZE;
        requireRange(archive, nameOffset, (long) nameLength + extraLength);
        byte[] localName = Arrays.copyOfRange(
                archive, Math.toIntExact(nameOffset), Math.toIntExact(nameOffset + nameLength));
        if (flags != entry.flags()
                || method != entry.method()
                || !Arrays.equals(localName, entry.name())) {
            throw corruptArchive();
        }
        boolean hasDescriptor = (flags & DATA_DESCRIPTOR_FLAG) != 0;
        validateLocalSizes(entry, hasDescriptor, crc, compressedSize, uncompressedSize);
        long dataOffset = nameOffset + nameLength + extraLength;
        long dataEnd = dataOffset + entry.compressedSize();
        requireRange(archive, dataOffset, entry.compressedSize());
        if (hasDescriptor) {
            validateDataDescriptor(archive, dataEnd, centralOffset, entry);
        } else if (dataEnd != centralOffset) {
            throw corruptArchive();
        }
    }

    private void validateLocalSizes(
            CentralEntry entry,
            boolean hasDescriptor,
            long crc,
            long compressedSize,
            long uncompressedSize) {
        if (!hasDescriptor) {
            if (crc != entry.crc()
                    || compressedSize != entry.compressedSize()
                    || uncompressedSize != entry.uncompressedSize()) {
                throw corruptArchive();
            }
            return;
        }
        if ((crc != 0 && crc != entry.crc())
                || (compressedSize != 0 && compressedSize != entry.compressedSize())
                || (uncompressedSize != 0 && uncompressedSize != entry.uncompressedSize())) {
            throw corruptArchive();
        }
    }

    private void validateDataDescriptor(
            byte[] archive, long dataEnd, long centralOffset, CentralEntry entry) {
        long descriptorSize = centralOffset - dataEnd;
        int offset = Math.toIntExact(dataEnd);
        if (descriptorSize == 16) {
            requireRange(archive, dataEnd, 16);
            if (unsignedInt(archive, offset) != DATA_DESCRIPTOR_SIGNATURE) {
                throw corruptArchive();
            }
            offset += Integer.BYTES;
        } else if (descriptorSize == 12) {
            requireRange(archive, dataEnd, 12);
        } else {
            throw corruptArchive();
        }
        if (unsignedInt(archive, offset) != entry.crc()
                || unsignedInt(archive, offset + 4) != entry.compressedSize()
                || unsignedInt(archive, offset + 8) != entry.uncompressedSize()) {
            throw corruptArchive();
        }
    }

    private void requireRange(byte[] archive, long offset, long length) {
        if (offset < 0 || length < 0 || offset > archive.length - length) {
            throw corruptArchive();
        }
    }

    private int unsignedShort(byte[] source, int offset) {
        return (source[offset] & 0xFF) | (source[offset + 1] & 0xFF) << 8;
    }

    private long unsignedInt(byte[] source, int offset) {
        return (source[offset] & 0xFFL)
                | (source[offset + 1] & 0xFFL) << 8
                | (source[offset + 2] & 0xFFL) << 16
                | (source[offset + 3] & 0xFFL) << 24;
    }

    private AiSkillImportException invalidArchive() {
        return AiSkillImportException.badRequest(INVALID_ARCHIVE_MESSAGE);
    }

    private AiSkillImportException corruptArchive() {
        return AiSkillImportException.badRequest(CORRUPT_ARCHIVE_MESSAGE);
    }

    private record Eocd(int entryCount, long centralOffset, long centralSize) {
    }

    private record CentralEntry(
            byte[] name,
            int flags,
            int method,
            long crc,
            long compressedSize,
            long uncompressedSize,
            long localOffset,
            long nextCentralOffset) {
    }
}