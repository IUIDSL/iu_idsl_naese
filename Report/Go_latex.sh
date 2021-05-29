#!/bin/sh
#
#
bibtex NativeAmericanEthnobotany.aux 
#
#intmode="nonstopmode"
intmode="errorstopmode"
#
pdflatex \
	-synctex=1 \
	-interaction=$intmode \
	NativeAmericanEthnobotany.tex
#
