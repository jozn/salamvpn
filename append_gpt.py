import os
import re

def should_ignore_file(file_path):
    """
    Checks if the file contains 'IGNORE_GPT' keyword.

    :param file_path: Path of the file to read and check.
    :return: True if 'IGNORE_GPT' is present, False otherwise.
    """
    with open(file_path, 'r') as file:
        return 'IGNORE_GPT' in file.read()

def remove_comments_and_imports(file_content):
    file_content = re.sub(r'/\*.*?\*/', '', file_content, flags=re.DOTALL)
    file_content = re.sub(r'//.*', '', file_content)
    file_content = re.sub(r'import\s+[\w\.]+(\.\*)?', '', file_content)
    return file_content

def append_file_content(outfile, file_path):
    """
    Appends the content of the specified file to the outfile, after processing.

    :param outfile: Open file object for writing.
    :param file_path: Path of the file to read and append.
    """
    if should_ignore_file(file_path):
        return

    with open(file_path, 'r') as infile:
        content = infile.read()
        cleaned_content = remove_comments_and_imports(content)
#         outfile.write(f"----- Start of {file_path} -----\n")
        outfile.write(cleaned_content)
#         outfile.write(f"\n----- End of {file_path} -----\n\n")

def combine_kotlin_java_sources(directories, output_file):
    """
    Walks through the given directories, finds all Kotlin and Java source files,
    processes them and appends their content into a single output file.

    :param directories: List of directories to search in.
    :param output_file: Path to the output file.
    """
    with open(output_file, 'w') as outfile:
        append_file_content(outfile, "./chatgpt.txt")
        for directory in directories:
            for root, dirs, files in os.walk(directory):
                for file in files:
                    if file.endswith(('.kt', '.java')):
                        file_path = os.path.join(root, file)
                        append_file_content(outfile, file_path)


# Example usage
# directories = ['./app', './core/src/main/java', './design/src']
directories = ['./app', './design/src']
# directories = ['./app', './core/src/main/java']
output_file = 'combined_sources.txt'
combine_kotlin_java_sources(directories, output_file)
