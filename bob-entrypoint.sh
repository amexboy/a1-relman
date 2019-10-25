#!/bin/sh

#   This file is part of Relman.
#
#   Relman is free software: you can redistribute it and/or modify
#   it under the terms of the GNU General Public License as published by
#   the Free Software Foundation, either version 3 of the License, or
#   (at your option) any later version.
#
#   Relman is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
#   GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public License
#   along with Relman. If not, see <http://www.gnu.org/licenses/>.

set -e

/usr/local/bin/dockerd-entrypoint.sh &

/opt/jdk/bin/java -jar /opt/relman-standalone.jar
