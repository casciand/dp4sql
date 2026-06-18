import gdown
import zipfile

def download_from_gdrive(file_id: str, output_path: str):
    url = f"https://drive.google.com/uc?id={file_id}"
    gdown.download(url, output_path, quiet=False)


if __name__ == "__main__":
    #https://drive.google.com/file/d/1vzHVi0gCxSHSr6XDJcG3jemQA1QwTLnV/view?usp=share_link
    download_from_gdrive("1vzHVi0gCxSHSr6XDJcG3jemQA1QwTLnV", "raw.zip")
    with zipfile.ZipFile("raw.zip", "r") as zip_ref:
        zip_ref.extractall(".")

    #https://drive.google.com/file/d/1vzHVi0gCxSHSr6XDJcG3jemQA1QwTLnV/view?usp=share_link
    download_from_gdrive("1NRsxvXIyRl19vjY6KxapxzEhoFvzU8a4", "output.zip")
    with zipfile.ZipFile("output.zip", "r") as zip_ref:
        zip_ref.extractall(".")