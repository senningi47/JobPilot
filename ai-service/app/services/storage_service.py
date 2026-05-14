import os
import time
from abc import ABC, abstractmethod


class StorageService(ABC):
    """Abstract base class for file storage backends."""

    @abstractmethod
    def save(self, user_id: str, filename: str, content: bytes) -> str:
        """Persist file content and return the storage path."""
        ...

    @abstractmethod
    def read(self, path: str) -> bytes:
        """Read file content from storage."""
        ...

    @abstractmethod
    def delete(self, path: str) -> bool:
        """Delete a file from storage. Returns True on success."""
        ...


class LocalStorageService(StorageService):
    """Stores uploaded files on the local filesystem."""

    def __init__(self) -> None:
        self.base_dir = os.environ.get("UPLOAD_DIR", "/uploads")

    def save(self, user_id: str, filename: str, content: bytes) -> str:
        user_dir = os.path.join(self.base_dir, user_id)
        os.makedirs(user_dir, exist_ok=True)

        timestamp = int(time.time() * 1000)
        stored_name = f"{timestamp}_{filename}"
        path = os.path.join(user_dir, stored_name)

        with open(path, "wb") as f:
            f.write(content)

        return path

    def read(self, path: str) -> bytes:
        with open(path, "rb") as f:
            return f.read()

    def delete(self, path: str) -> bool:
        if os.path.exists(path):
            os.remove(path)
            return True
        return False


class OSSStorageService(StorageService):
    """Stub for Alibaba Cloud OSS storage backend."""

    def __init__(self) -> None:
        raise NotImplementedError(
            "OSSStorageService is not yet implemented. "
            "Use LocalStorageService for development."
        )

    def save(self, user_id: str, filename: str, content: bytes) -> str:
        raise NotImplementedError

    def read(self, path: str) -> bytes:
        raise NotImplementedError

    def delete(self, path: str) -> bool:
        raise NotImplementedError
