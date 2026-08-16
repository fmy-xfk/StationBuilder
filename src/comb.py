import os
import pathlib

def combine_py_files_to_md(root_dir, output_md):
    """
    Combines all .py files in the root directory and its subdirectories into a single Markdown file.
    Each .py file is represented as a section with a header and a code block.
    """
    with open(output_md, 'w', encoding='utf-8') as md_file:
        for java_file in pathlib.Path(root_dir).rglob('*.java'):
            relative_path = java_file.relative_to(root_dir)
            if relative_path.name == pathlib.Path(__file__).name:
                continue
            md_file.write(f"## {relative_path}\n\n")
            md_file.write("```java\n")
            try:
                with open(java_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    md_file.write(content)
            except Exception as e:
                md_file.write(f"# Error reading file: {e}\n")
            md_file.write("\n```\n\n")

if __name__ == "__main__":
    current_dir = os.path.dirname(os.path.abspath(__file__))
    output_file = os.path.join(current_dir, "station_builder.md")
    combine_py_files_to_md(current_dir, output_file)
    print(f"Combined Markdown file created at: {output_file}")