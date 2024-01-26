# Contribution

## Table of contents

- [Contribution](#contribution)
    - [Table of contents](#table-of-contents)
    - [Git](#git)
        - [Repositories](#repositories)
    - [Editor](#editor)
        - [Python](#python)
    - [References](#references)

## Git

- Please branch out of `dev` and do stuff
    - Developers should have `dev-NAME` branch
    - If issue (or milestone) is large and requires branching further, create branch by milestone name and include issue number `#NUM` in commit (maybe create a blank commit)
- Branch `main` is only for PR merges (no push allowed, avoid `--force` - only if necessary)
- Keep your TODOs on your local machine in `TODO-YOURNAME.md` file (gitignore will ignore them)

### Repositories

- Clone repository, `cd` into it, `rm -rf ./.git` for it, note timestamp using `date -Isec`, add entry to [main README](./README.md). We'll see [submodules](https://github.blog/2016-02-01-working-with-submodules/) later.
    - Make sure that the name is `snake_case` so that we can use it as a module.

## Editor

- Tab indentation is 4 spaces
