from pathlib import Path

from setuptools import setup

ROOT = Path(__file__).parent

setup(
    name='glasstone',
    version='0.0.1',
    description='Python library for modelling nuclear weapons effects',
    long_description=(ROOT / 'README.md').read_text(encoding='utf-8'),
    long_description_content_type='text/markdown',
    author='Edward Geist',
    author_email='egeist@stanford.edu',
    url='https://github.com/GOFAI/glasstone',
    license='MIT',
    packages=['glasstone'],
    install_requires=['numpy', 'scipy', 'affine'],
    python_requires='>=3.8')
